package com.example.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 简单的测试应用，用于远程调试测试
 */
public class DemoApplication {
    
    private static int counter = 0;
    private static final String LOG_FILE = "/tmp/demo-app-status.log";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static void main(String[] args) throws Exception {
        // 添加 shutdown hook 来检测进程退出
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("⚠️  JVM Shutdown Hook triggered! Application is exiting...");
            System.err.println("⚠️  Application is shutting down!");
        }));
        
        log("✅ Demo Application started! PID: " + ProcessHandle.current().pid());
        System.out.println("Demo Application started! PID: " + ProcessHandle.current().pid());
        System.out.println("JDWP port should be listening...");
        System.out.println("Log file: " + LOG_FILE);
        System.out.println("Waiting for debugger to attach...");
        
        // 主循环
        while (true) {
            try {
                processData();
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                log("⚠️  Thread interrupted: " + e.getMessage());
                System.err.println("Thread interrupted, but continuing...");
                // 不退出，继续运行
            } catch (Exception e) {
                log("❌ Error in main loop: " + e.getMessage());
                e.printStackTrace();
                // 发生错误也不退出
            }
        }
    }
    
    private static void log(String message) {
        try {
            String timestamp = LocalDateTime.now().format(formatter);
            String logLine = "[" + timestamp + "] " + message + "\n";
            Files.write(Paths.get(LOG_FILE), logLine.getBytes(), 
                       StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 忽略日志错误
        }
    }
    
    /**
     * 这个方法会被 JDI Debugger 设置断点
     */
    public static void processData() {
        counter++;
        String message = "Processing data #" + counter;
        long timestamp = System.currentTimeMillis();
        
        System.out.println(message + " at " + timestamp);
        log(message);
        
        // 一些简单的计算
        int result = calculate(counter);
        System.out.println("  Result: " + result);
        
        // 每 10 次输出一次状态
        if (counter % 10 == 0) {
            System.out.println("📊 Status: Application is running, counter=" + counter);
            log("📊 Status check: counter=" + counter + ", threads=" + Thread.activeCount());
        }
    }
    
    private static int calculate(int value) {
        return value * 2 + 10;
    }
}
