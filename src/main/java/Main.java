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
            ParsedCommand parsed = parseCommandLine(userinput);
            if (parsed.args.isEmpty()) {
                continue;
            }
            String[] parts = parsed.args.toArray(new String[0]);
            String command = parts[0];
            
            executeCommand(command, parts, parsed.redirectFile, parsed.redirectStderrFile);
        }
        sc.close();
    }

    private static final java.util.List<String> BUILTINS = java.util.Arrays.asList("exit", "echo", "type", "pwd","cd");

    private static void executeCommand(String command, String[] parts, String redirectFile, String redirectStderrFile) {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream fileOut = null;
        if (redirectFile != null) {
            try {
                File file = new File(redirectFile);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                fileOut = new java.io.PrintStream(new java.io.FileOutputStream(file));
                System.setOut(fileOut);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                return;
            }
        }

        java.io.PrintStream originalErr = System.err;
        java.io.PrintStream fileErr = null;
        if (redirectStderrFile != null) {
            try {
                File file = new File(redirectStderrFile);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                fileErr = new java.io.PrintStream(new java.io.FileOutputStream(file));
                System.setErr(fileErr);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                if (fileOut != null) {
                    fileOut.close();
                    System.setOut(originalOut);
                }
                return;
            }
        }

        try {
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
                            if (redirectFile != null) {
                                pb.redirectOutput(new File(redirectFile));
                            } else {
                                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            }
                            if (redirectStderrFile != null) {
                                pb.redirectError(new File(redirectStderrFile));
                            } else {
                                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                            }
                            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
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
        } finally {
            if (fileOut != null) {
                fileOut.close();
                System.setOut(originalOut);
            }
            if (fileErr != null) {
                fileErr.close();
                System.setErr(originalErr);
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

    private static ParsedCommand parseCommandLine(String input) {
        java.util.List<String> args = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean inArg = false;
        String redirectFile = null;
        String redirectStderrFile = null;
        boolean expectingRedirectFile = false;
        boolean expectingStderrRedirectFile = false;

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
                } else if (c == '>') {
                    if (inArg) {
                        if (current.length() == 1 && current.charAt(0) == '1') {
                            current.setLength(0);
                            expectingRedirectFile = true;
                        } else if (current.length() == 1 && current.charAt(0) == '2') {
                            current.setLength(0);
                            expectingStderrRedirectFile = true;
                        } else {
                            if (expectingRedirectFile) {
                                redirectFile = current.toString();
                                expectingRedirectFile = false;
                            } else if (expectingStderrRedirectFile) {
                                redirectStderrFile = current.toString();
                                expectingStderrRedirectFile = false;
                            } else {
                                args.add(current.toString());
                            }
                            current.setLength(0);
                            expectingRedirectFile = true;
                        }
                    } else {
                        expectingRedirectFile = true;
                    }
                    inArg = false;
                } else if (Character.isWhitespace(c)) {
                    if (inArg) {
                        if (expectingRedirectFile) {
                            redirectFile = current.toString();
                            expectingRedirectFile = false;
                        } else if (expectingStderrRedirectFile) {
                            redirectStderrFile = current.toString();
                            expectingStderrRedirectFile = false;
                        } else {
                            args.add(current.toString());
                        }
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
            if (expectingRedirectFile) {
                redirectFile = current.toString();
            } else if (expectingStderrRedirectFile) {
                redirectStderrFile = current.toString();
            } else {
                args.add(current.toString());
            }
        }
        return new ParsedCommand(args, redirectFile, redirectStderrFile);
    }

    private static class ParsedCommand {
        public final java.util.List<String> args;
        public final String redirectFile;
        public final String redirectStderrFile;

        public ParsedCommand(java.util.List<String> args, String redirectFile, String redirectStderrFile) {
            this.args = args;
            this.redirectFile = redirectFile;
            this.redirectStderrFile = redirectStderrFile;
        }
    }
}