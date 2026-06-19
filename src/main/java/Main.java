import java.util.Scanner;
import java.io.File;

public class Main {
    private static String currentDirectory = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            if (!sc.hasNextLine()) {
                break;
            }
            String userinput = sc.nextLine();
            String trimmedInput = userinput.trim();
            if (trimmedInput.isEmpty()) {
                continue;
            }
            String[] parts = trimmedInput.split("\\s+");
            String command = parts[0];
            
            executeCommand(command, parts);
        }
        sc.close();
    }

    private static final java.util.List<String> BUILTINS = java.util.Arrays.asList("exit", "echo", "type", "pwd","cd");

    private static void executeCommand(String command, String[] parts) {
        if (command.equals("exit")) {
            handleExit(parts);
        } else if (command.equals("echo")) {
            handleEcho(parts);
        } else if (command.equals("type")) {
            handleType(parts);
        } else if (command.equals("pwd")) {
            handlePwd(parts);
        } else if(command.equals("cd")){
            handleCD(parts);
        }
         else {
            String path = getExecutablePath(command);
            if (path != null) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.directory(new File(currentDirectory));
                    pb.inheritIO();
                    Process process = pb.start();
                    process.waitFor();
                } catch (Exception e) {
                    System.out.println(command + ": command not found");
                }
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }

    private static void handleExit(String[] parts) {
        if (parts.length > 1) {
            try {
                System.exit(Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                System.exit(0);
            }
        } else {
            System.exit(0);
        }
    }

    private static void handleEcho(String[] parts) {
        String output = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        System.out.println(output);
    }

    private static void handleType(String[] parts) {
        if (parts.length > 1) {
            String targetCmd = parts[1];
            if (BUILTINS.contains(targetCmd)) {
                System.out.println(targetCmd + " is a shell builtin");
            } else {
                String exePath = getExecutablePath(targetCmd);
                if (exePath != null) {
                    System.out.println(targetCmd + " is " + exePath);
                } else {
                    System.out.println(targetCmd + ": not found");
                }
            }
        }
    }

    private static void handlePwd(String[] parts) {
        System.out.println(currentDirectory);
    }

    private static void handleCD(String[] parts) {
        String targetDir = "~";
        if (parts.length > 1) {
            targetDir = parts[1];
        }
        File targetDirFile;
        if (targetDir.equals("~")) {
            targetDirFile = new File(System.getenv("HOME"));
        } else if (targetDir.startsWith("~/")) {
            targetDirFile = new File(System.getenv("HOME"), targetDir.substring(2));
        } else if (targetDir.startsWith("/")) {
            targetDirFile = new File(targetDir);
        } else {
            targetDirFile = new File(currentDirectory, targetDir);
        }

        try {
            File canonicalFile = targetDirFile.getCanonicalFile();
            if (canonicalFile.exists() && canonicalFile.isDirectory()) {
                currentDirectory = canonicalFile.getAbsolutePath();
            } else {
                System.out.println("cd: " + targetDir + ": No such file or directory");
            }
        } catch (Exception e) {
            System.out.println("cd: " + targetDir + ": No such file or directory");
        }
    }

    private static String getExecutablePath(String command) {
        if (command.contains("/")) {
            File file = new File(command);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
            return null;
        }
        String path = System.getenv("PATH");
        if (path == null || path.isEmpty()) {
            return null;
        }
        String[] pathDirs = path.split(":");
        for (String dir : pathDirs) {
            File file = new File(dir, command);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}