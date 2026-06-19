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
            java.util.List<String> parsed = parseCommandLine(userinput);
            if (parsed.isEmpty()) {
                continue;
            }
            String[] parts = parsed.toArray(new String[0]);
            String command = parts[0];
            
            executeCommand(command, parts);
        }
        sc.close();
    }

    private static final java.util.List<String> BUILTINS = java.util.Arrays.asList("exit", "echo", "type", "pwd","cd");

    private static void executeCommand(String command, String[] parts) {
        switch (command) {
            case "exit":
                handleExit(parts);
                break;
            case "echo":
                handleEcho(parts);
                break;
            case "type":
                handleType(parts);
                break;
            case "pwd":
                handlePwd(parts);
                break;
            case "cd":
                handleCD(parts);
                break;
            default:
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
                break;
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

    private static java.util.List<String> parseCommandLine(String input) {
        java.util.List<String> args = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean inArg = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuotes) {
                if (c == '"') {
                    inDoubleQuotes = false;
                } else if (c == '\\') {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '$' || next == '`' || next == '"' || next == '\\' || next == '\n') {
                            current.append(next);
                            i++;
                        } else {
                            current.append(c);
                        }
                    } else {
                        current.append(c);
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '\'') {
                    inSingleQuotes = true;
                    inArg = true;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                    inArg = true;
                } else if (c == '\\') {
                    if (i + 1 < input.length()) {
                        i++;
                        current.append(input.charAt(i));
                        inArg = true;
                    }
                } else if (Character.isWhitespace(c)) {
                    if (inArg) {
                        args.add(current.toString());
                        current.setLength(0);
                        inArg = false;
                    }
                } else {
                    current.append(c);
                    inArg = true;
                }
            }
        }
        if (inArg) {
            args.add(current.toString());
        }
        return args;
    }
}