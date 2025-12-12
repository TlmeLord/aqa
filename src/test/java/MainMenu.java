import java.io.*;
import java.util.*;

public class MainMenu {
    private static Process mockProcess;
    private static Process appProcess;
    private static final String JAR_NAME = "internal-0.0.1-SNAPSHOT.jar";

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- LAUNCHER ---");
            System.out.println("1. Start Mock server");
            System.out.println("2. Start Application (jar)");
            System.out.println("3. Run tests (mvn test)");
            System.out.println("4. Open Allure report");
            System.out.println("5. Show logs");
            System.out.println("0. Exit");
            System.out.print("Select menu item: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> runMock();
                case "2" -> runApp();
                case "3" -> runTests();
                case "4" -> showAllure();
                case "5" -> showLogs();
                case "0" -> {
                    destroyAll();
                    scanner.close();
                    System.out.println("Bye!");
                    return;
                }
                default -> System.out.println("Unknown option!");
            }
        }
    }

    static void runMock() throws Exception {
        // Завершаем старый процесс mock, если был
        if (mockProcess != null && mockProcess.isAlive()) {
            System.out.println("Stopping previous Mock instance...");
            mockProcess.destroy();
            Thread.sleep(800); // дать завершиться корректно
        }

        System.out.println("Starting Mock server...");
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", "src/test/java", "MockServer");
            pb.redirectOutput(new File("mock.log"));
            pb.redirectErrorStream(true);
            mockProcess = pb.start();

            // Ждем немного для инициализации
            Thread.sleep(1000);

            // Проверяем, запустился ли процесс
            if (mockProcess.isAlive()) {
                // Проверяем доступность порта 8888
                if (isPortAvailable(8888)) {
                    System.out.println("❌ ERROR: Mock server failed to start - port 8888 is not listening");
                    mockProcess.destroy();
                    mockProcess = null;
                } else {
                    System.out.println("✅ SUCCESS: Mock server started on port 8888");
                }
            } else {
                System.out.println("❌ ERROR: Mock server process terminated unexpectedly");
                mockProcess = null;
            }
        } catch (Exception e) {
            System.out.println("❌ ERROR: Failed to start Mock server: " + e.getMessage());
            mockProcess = null;
        }
    }

    static void runApp() throws Exception {
        // Завершаем старый процесс app, если был
        if (appProcess != null && appProcess.isAlive()) {
            System.out.println("Stopping previous Application instance...");
            appProcess.destroy();
            Thread.sleep(1200); // дать корректно завершиться
        }

        System.out.println("Starting application (jar)...");

        // Проверяем доступность порта перед запуском
        boolean portWasFree = isPortAvailable(8080);
        System.out.println("Port 8080 was " + (portWasFree ? "free" : "occupied") + " before starting application");

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", "-Dsecret=qazWSXedc", "-Dmock=http://localhost:8888/", JAR_NAME);
            pb.redirectOutput(new File("app.log"));
            pb.redirectErrorStream(true);
            appProcess = pb.start();

            // Ждем дольше для инициализации приложения
            System.out.println("Waiting for application to start...");
            Thread.sleep(5000); // увеличиваем время ожидания до 5 секунд

            // Проверяем, запустился ли процесс
            if (appProcess.isAlive()) {
                // Проверяем доступность порта 8080
                if (isPortAvailable(8080)) {
                    System.out.println("❌ ERROR: Application failed to start - port 8080 is not listening after 5 seconds");
                    System.out.println("Check app.log for details");

                    // Проверяем, на каких портах вообще слушает приложение
                    checkApplicationPorts();

                    // Показываем последние строки лога для диагностики
                    try {
                        printLastLines("app.log", "APP (last 10 lines)");
                    } catch (Exception e) {
                        System.out.println("Could not read app.log: " + e.getMessage());
                    }

                    appProcess.destroy();
                    appProcess = null;
                } else {
                    System.out.println("✅ SUCCESS: Application started on port 8080");
                }
            } else {
                System.out.println("❌ ERROR: Application process terminated unexpectedly");

                // Показываем лог для диагностики
                try {
                    printLastLines("app.log", "APP (error log)");
                } catch (Exception e) {
                    System.out.println("Could not read app.log: " + e.getMessage());
                }

                appProcess = null;
            }
        } catch (Exception e) {
            System.out.println("❌ ERROR: Failed to start Application: " + e.getMessage());
            appProcess = null;
        }
    }

    static void runTests() throws Exception {
        System.out.println("Running tests...");
        try {
            String mvnCmd = findMavenCmd();
            Process testProcess = new ProcessBuilder(mvnCmd, "test").start();

            // Выводим вывод в реальном времени
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(testProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = testProcess.waitFor();
            if (exitCode == 0) {
                System.out.println("✅ SUCCESS: Tests completed successfully");
            } else {
                System.out.println("❌ ERROR: Tests failed with exit code " + exitCode);
            }
        } catch (Exception e) {
            System.out.println("❌ ERROR: Failed to run tests: " + e.getMessage());
        }
    }

    static void showAllure() throws Exception {
        System.out.println("Opening Allure report...");
        try {
            String mvnCmd = findMavenCmd();
            Process allureProcess = new ProcessBuilder(mvnCmd, "allure:serve").start();

            // Ждем немного для инициализации
            Thread.sleep(2000);

            if (allureProcess.isAlive()) {
                System.out.println("✅ SUCCESS: Allure report opened in browser");
            } else {
                System.out.println("❌ ERROR: Allure report failed to start");
            }
        } catch (Exception e) {
            System.out.println("❌ ERROR: Failed to start Allure report: " + e.getMessage());
        }
    }

    static void showLogs() throws Exception {
        printLastLines("mock.log", "MOCK");
        printLastLines("app.log", "APP");
    }

    static void printLastLines(String file, String label) throws IOException {
        System.out.println("\n--- LOGS " + label + " ---");
        File log = new File(file);
        if (!log.exists()) {
            System.out.println("Log file not found.");
            return;
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(log))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        lines.stream().skip(Math.max(0, lines.size() - 30)).forEach(System.out::println);
    }

    static void runAndPrint(String[] cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
        p.waitFor();
    }

    static void destroyAll() {
        System.out.println("Stopping all processes...");

        // Сначала пытаемся корректно завершить наши процессы
        if (mockProcess != null && mockProcess.isAlive()) {
            System.out.println("Stopping Mock server process...");
            mockProcess.destroy();
        }

        if (appProcess != null && appProcess.isAlive()) {
            System.out.println("Stopping Application process...");
            appProcess.destroy();
        }

        try {
            Thread.sleep(1000); // ждем завершения
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Целенаправленно убиваем процессы по портам
        killProcessByPort(8888, "Mock server");
        killProcessByPort(8080, "Application");
        killAllureProcesses();

        System.out.println("All processes stopped.");
    }

    private static void killProcessByPort(int port, String serviceName) {
        try {
            System.out.println("Checking for " + serviceName + " on port " + port + "...");

            // Находим PID процесса по порту
            Process netstat = new ProcessBuilder("netstat", "-ano").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(netstat.getInputStream()));
            String line;
            String pid = null;

            while ((line = reader.readLine()) != null) {
                if (line.contains(":" + port) && line.contains("LISTENING")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 4) {
                        pid = parts[parts.length - 1];
                        break;
                    }
                }
            }
            reader.close();
            netstat.waitFor();

            if (pid != null && !pid.equals("0")) {
                System.out.println("Found " + serviceName + " process (PID: " + pid + "), terminating...");
                Process taskkill = new ProcessBuilder("taskkill", "/PID", pid, "/F").start();
                taskkill.waitFor();
                System.out.println(serviceName + " terminated.");
            } else {
                System.out.println("No " + serviceName + " found on port " + port);
            }

        } catch (Exception e) {
            System.out.println("Error stopping " + serviceName + ": " + e.getMessage());
        }
    }

    private static void killAllureProcesses() {
        try {
            System.out.println("Checking for Allure processes...");
            List<String> pids = new ArrayList<>();

            try {
                Process tasklistAllure = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq allure.bat", "/FO", "CSV", "/NH").start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(tasklistAllure.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        String pid = parts[1].replace("\"", "");
                        if (!pid.equals("0")) {
                            pids.add(pid);
                        }
                    }
                }
                reader.close();
                tasklistAllure.waitFor();
            } catch (Exception e) {
            }

            try {
                Process tasklistJava = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq java.exe", "/FO", "CSV", "/NH").start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(tasklistJava.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        String pid = parts[1].replace("\"", "");
                        try {
                            Process wmic = new ProcessBuilder("wmic", "process", "where", "ProcessId=" + pid, "get", "CommandLine", "/VALUE").start();
                            BufferedReader wmicReader = new BufferedReader(new InputStreamReader(wmic.getInputStream()));
                            String cmdLine = wmicReader.readLine(); // читаем строку вида CommandLine=...
                            wmicReader.close();
                            wmic.waitFor();

                            if (cmdLine != null && (cmdLine.contains("allure") || cmdLine.contains("allure:serve"))) {
                                pids.add(pid);
                            }
                        } catch (Exception e) {
                        }
                    }
                }
                reader.close();
                tasklistJava.waitFor();
            } catch (Exception e) {
            }

            try {
                Process netstat = new ProcessBuilder("netstat", "-ano").start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(netstat.getInputStream()));
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.contains("0.0.0.0:") && line.contains("LISTENING")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length > 4) {
                            String pid = parts[parts.length - 1];
                            if (!pid.equals("0") && !pids.contains(pid)) {
                                try {
                                    Process wmic = new ProcessBuilder("wmic", "process", "where", "ProcessId=" + pid, "get", "Name", "/VALUE").start();
                                    BufferedReader wmicReader = new BufferedReader(new InputStreamReader(wmic.getInputStream()));
                                    String nameLine = wmicReader.readLine();
                                    wmicReader.close();
                                    wmic.waitFor();

                                    if (nameLine != null && nameLine.contains("java.exe")) {
                                        pids.add(pid);
                                    }
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                }
                reader.close();
                netstat.waitFor();
            } catch (Exception e) {

            }

            for (String pid : pids) {
                try {
                    System.out.println("Found potential Allure process (PID: " + pid + "), terminating...");
                    Process taskkill = new ProcessBuilder("taskkill", "/PID", pid, "/F").start();
                    taskkill.waitFor();
                } catch (Exception e) {
                    System.out.println("Error terminating process " + pid + ": " + e.getMessage());
                }
            }

            if (pids.isEmpty()) {
                System.out.println("No Allure processes found.");
            } else {
                System.out.println("Allure processes terminated (" + pids.size() + " processes).");
            }

        } catch (Exception e) {
            System.out.println("Error stopping Allure: " + e.getMessage());
        }
    }

    private static boolean isPortAvailable(int port) {
        try {
            Process netstat = new ProcessBuilder("netstat", "-ano").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(netstat.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.contains(":" + port) && line.contains("LISTENING")) {
                    reader.close();
                    netstat.waitFor();
                    return false; // Порт занят
                }
            }
            reader.close();
            netstat.waitFor();
            return true; // Порт свободен

        } catch (Exception e) {
            System.out.println("Warning: Could not check port " + port + ": " + e.getMessage());
            return true;
        }
    }

    private static void checkApplicationPorts() {
        try {
            System.out.println("Checking what ports the application is listening on...");
            Process netstat = new ProcessBuilder("netstat", "-ano").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(netstat.getInputStream()));
            String line;
            boolean foundPorts = false;

            while ((line = reader.readLine()) != null) {
                if (line.contains("LISTENING") && (line.contains("127.0.0.1:") || line.contains("0.0.0.0:") || line.contains("localhost:"))) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 4) {
                        String localAddress = parts[1];
                        String pid = parts[parts.length - 1];
                        System.out.println("  Found listening port: " + localAddress + " (PID: " + pid + ")");
                        foundPorts = true;
                    }
                }
            }
            reader.close();
            netstat.waitFor();

            if (!foundPorts) {
                System.out.println("  No listening ports found for application");
            }

        } catch (Exception e) {
            System.out.println("Error checking application ports: " + e.getMessage());
        }
    }

    private static String findMavenCmd() {
        String pathVar = System.getenv("PATH");
        if (pathVar == null) return "mvn";
        String[] paths = pathVar.split(File.pathSeparator);
        String[] commands = {"mvn.cmd", "mvn.bat", "mvn.exe", "mvn"};
        for (String dir : paths) {
            for (String cmd : commands) {
                File f = new File(dir, cmd);
                if (f.exists() && f.isFile()) {
                    return f.getAbsolutePath();
                }
            }
        }
        return "mvn";
    }
}
