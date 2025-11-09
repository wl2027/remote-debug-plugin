package com.example.debugger;

import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;

import java.util.List;
import java.util.Map;

/**
 * Simple JDI Debugger to test the proxy setup
 * Simulates IDEA's remote debugging functionality
 */
public class SimpleJDIDebugger {
    
    private VirtualMachine vm;
    private static final String TARGET_CLASS = "com.example.demo.DemoApplication";
    private static final String TARGET_METHOD = "processData";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java SimpleJDIDebugger <host> <port>");
            System.out.println("Example: java SimpleJDIDebugger localhost 5005");
            System.exit(1);
        }
        
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        
        SimpleJDIDebugger debugger = new SimpleJDIDebugger();
        try {
            debugger.connect(host, port);
            debugger.setBreakpoint();
            debugger.eventLoop();
        } catch (Exception e) {
            System.err.println("Debugger error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Connect to the remote JVM through the proxy
     */
    public void connect(String host, int port) throws Exception {
        System.out.println("Connecting to target JVM at " + host + ":" + port + "...");
        
        // Get the Socket Attaching Connector
        VirtualMachineManager vmManager = Bootstrap.virtualMachineManager();
        AttachingConnector connector = null;
        
        for (AttachingConnector conn : vmManager.attachingConnectors()) {
            if (conn.name().equals("com.sun.jdi.SocketAttach")) {
                connector = conn;
                break;
            }
        }
        
        if (connector == null) {
            throw new RuntimeException("Socket Attaching Connector not found");
        }
        
        System.out.println("Using connector: " + connector.name());
        
        // Set connection arguments
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        
        // This is where we can add custom parameters as mentioned in requirements
        Connector.Argument hostnameArg = arguments.get("hostname");
        hostnameArg.setValue(host);
        
        Connector.Argument portArg = arguments.get("port");
        portArg.setValue(String.valueOf(port));
        
        // Optional: timeout
        Connector.Argument timeoutArg = arguments.get("timeout");
        if (timeoutArg != null) {
            timeoutArg.setValue("10000");
        }
        
        System.out.println("Connection arguments:");
        for (Map.Entry<String, Connector.Argument> entry : arguments.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue().value());
        }
        
        // Connect to the JVM
        vm = connector.attach(arguments);
        System.out.println("Successfully connected to target JVM!");
        System.out.println("Target VM: " + vm.name() + " (version " + vm.version() + ")");
    }
    
    /**
     * Set a breakpoint in the target application
     */
    public void setBreakpoint() {
        System.out.println("\nSetting up breakpoint...");
        
        EventRequestManager erm = vm.eventRequestManager();
        
        // Request notification when the target class is loaded
        ClassPrepareRequest classPrepareRequest = erm.createClassPrepareRequest();
        classPrepareRequest.addClassFilter(TARGET_CLASS);
        classPrepareRequest.enable();
        
        System.out.println("Waiting for class " + TARGET_CLASS + " to be loaded...");
    }
    
    /**
     * Main event loop - processes debug events
     */
    public void eventLoop() throws Exception {
        System.out.println("\nEntering event loop...");
        EventQueue eventQueue = vm.eventQueue();
        boolean breakpointSet = false;
        int hitCount = 0;
        
        while (true) {
            EventSet eventSet = eventQueue.remove();
            
            for (Event event : eventSet) {
                if (event instanceof ClassPrepareEvent) {
                    // Class is loaded, now we can set the breakpoint
                    ClassPrepareEvent classPrepareEvent = (ClassPrepareEvent) event;
                    ReferenceType refType = classPrepareEvent.referenceType();
                    
                    if (refType.name().equals(TARGET_CLASS) && !breakpointSet) {
                        List<Method> methods = refType.methodsByName(TARGET_METHOD);
                        if (!methods.isEmpty()) {
                            Method method = methods.get(0);
                            Location location = method.location();
                            
                            BreakpointRequest breakpointRequest = vm.eventRequestManager().createBreakpointRequest(location);
                            breakpointRequest.enable();
                            
                            System.out.println("✓ Breakpoint set at " + TARGET_CLASS + "." + TARGET_METHOD + "()");
                            System.out.println("  Location: " + location);
                            breakpointSet = true;
                        }
                    }
                    
                } else if (event instanceof BreakpointEvent) {
                    // Breakpoint hit!
                    BreakpointEvent breakpointEvent = (BreakpointEvent) event;
                    hitCount++;
                    
                    System.out.println("\n========================================");
                    System.out.println("🎯 BREAKPOINT HIT #" + hitCount);
                    System.out.println("========================================");
                    
                    ThreadReference thread = breakpointEvent.thread();
                    System.out.println("Thread: " + thread.name());
                    System.out.println("Location: " + breakpointEvent.location());
                    
                    // Print stack trace
                    System.out.println("\nStack trace:");
                    List<StackFrame> frames = thread.frames();
                    for (int i = 0; i < Math.min(frames.size(), 5); i++) {
                        StackFrame frame = frames.get(i);
                        System.out.println("  [" + i + "] " + frame.location());
                    }
                    
                    // Try to read local variables
                    try {
                        StackFrame frame = thread.frame(0);
                        System.out.println("\nLocal variables:");
                        for (LocalVariable var : frame.visibleVariables()) {
                            Value value = frame.getValue(var);
                            System.out.println("  " + var.name() + " = " + value);
                        }
                    } catch (Exception e) {
                        System.out.println("  (Could not read local variables: " + e.getMessage() + ")");
                    }
                    
                    System.out.println("\nResuming execution...");
                    
                    // After 3 hits, demonstrate we can detach
                    if (hitCount >= 3) {
                        System.out.println("\n========================================");
                        System.out.println("Demonstrated successful debugging!");
                        System.out.println("Detaching from target JVM...");
                        System.out.println("========================================");
                        vm.dispose();
                        return;
                    }
                    
                } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                    System.out.println("\nTarget JVM disconnected");
                    return;
                }
            }
            
            // Resume the VM
            eventSet.resume();
        }
    }
}

