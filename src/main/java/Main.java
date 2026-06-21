import java.util.Scanner;
import java.io.File;

public class Main {
    private static String currentDirectory = System.getProperty("user.dir");
    private static final java.util.List<Job> activeJobs = new java.util.ArrayList<>();

    private static class Job {
        public final int jobNum;
        public final long pid;
        public final String command;
        public String status;
        public final Process process;

        public Job(int jobNum, long pid, String command, String status, Process process) {
            this.jobNum = jobNum;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.process = process;
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        while (true) {
            reapExitedJobs(System.out);
            System.out.print("$ ");
            if (!sc.hasNextLine()) {
                break;
            }
            String userinput = sc.nextLine();
            ParsedCommand parsed = parseCommandLine(userinput);
            if (parsed.commands.isEmpty() || parsed.commands.get(0).args.isEmpty()) {
                continue;
            }

            if (parsed.commands.size() > 1) {
                executePipeline(parsed.commands, parsed.runInBackground, userinput.trim(), System.out);
            } else {
                CommandSpec spec = parsed.commands.get(0);
                String[] parts = spec.args.toArray(new String[0]);
                String command = parts[0];
                executeCommand(command, parts, spec.redirectFile, spec.redirectStderrFile, spec.appendRedirect, spec.appendStderrRedirect, parsed.runInBackground, userinput.trim());
            }
        }
        sc.close();
    }

    private static final java.util.List<String> BUILTINS = java.util.Arrays.asList("exit", "echo", "type", "pwd","cd", "jobs");

    private static void executeCommand(String command, String[] parts, String redirectFile, String redirectStderrFile, boolean appendRedirect, boolean appendStderrRedirect, boolean runInBackground, String jobCommand) {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream fileOut = null;
        if (redirectFile != null) {
            try {
                File file = new File(redirectFile);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                fileOut = new java.io.PrintStream(new java.io.FileOutputStream(file, appendRedirect));
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
                fileErr = new java.io.PrintStream(new java.io.FileOutputStream(file, appendStderrRedirect));
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
                case "jobs":
                    handleJobs(originalOut);
                    break;
                default:
                    String path = getExecutablePath(command);
                    if (path != null) {
                        try {
                            ProcessBuilder pb = new ProcessBuilder(parts);
                            pb.directory(new File(currentDirectory));
                            if (redirectFile != null) {
                                if (appendRedirect) {
                                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(redirectFile)));
                                } else {
                                    pb.redirectOutput(new File(redirectFile));
                                }
                            } else {
                                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            }
                            if (redirectStderrFile != null) {
                                if (appendStderrRedirect) {
                                    pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(redirectStderrFile)));
                                } else {
                                    pb.redirectError(new File(redirectStderrFile));
                                }
                            } else {
                                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                            }
                            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                            Process process = pb.start();
                            if (runInBackground) {
                                int jobNum;
                                long pid = process.pid();
                                synchronized (activeJobs) {
                                    jobNum = getNextJobNumber();
                                    Job job = new Job(jobNum, pid, jobCommand, "Running", process);
                                    activeJobs.add(job);
                                }
                                originalOut.println("[" + jobNum + "] " + pid);
                            } else {
                                process.waitFor();
                            }
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

    private static void executePipeline(java.util.List<CommandSpec> commands, boolean runInBackground, String jobCommand, java.io.PrintStream originalOut) {
        java.util.List<ProcessBuilder> builders = new java.util.ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            CommandSpec spec = commands.get(i);
            String[] cmdParts = spec.args.toArray(new String[0]);
            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.directory(new File(currentDirectory));
            
            // Redirection logic for stdin
            if (i == 0) {
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }
            
            // Redirection logic for stdout
            if (i == commands.size() - 1) {
                if (spec.redirectFile != null) {
                    if (spec.appendRedirect) {
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(spec.redirectFile)));
                    } else {
                        pb.redirectOutput(new File(spec.redirectFile));
                    }
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
            }
            
            // Redirection logic for stderr
            if (spec.redirectStderrFile != null) {
                if (spec.appendStderrRedirect) {
                    pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(spec.redirectStderrFile)));
                } else {
                    pb.redirectError(new File(spec.redirectStderrFile));
                }
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }
            
            builders.add(pb);
        }

        try {
            java.util.List<Process> processes = ProcessBuilder.startPipeline(builders);
            if (runInBackground) {
                int jobNum;
                synchronized (activeJobs) {
                    jobNum = getNextJobNumber();
                    Process lastProcess = processes.get(processes.size() - 1);
                    Job job = new Job(jobNum, lastProcess.pid(), jobCommand, "Running", lastProcess);
                    activeJobs.add(job);
                }
                originalOut.println("[" + jobNum + "] " + processes.get(processes.size() - 1).pid());
            } else {
                for (Process process : processes) {
                    process.waitFor();
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to start pipeline: " + e.getMessage());
        }
    }

    private static void handleJobs(java.io.PrintStream originalOut) {
        synchronized (activeJobs) {
            java.util.List<Job> toRemove = new java.util.ArrayList<>();
            for (int i = 0; i < activeJobs.size(); i++) {
                Job job = activeJobs.get(i);
                if (job.process != null && !job.process.isAlive()) {
                    job.status = "Done";
                }
                char marker = ' ';
                if (i == activeJobs.size() - 1) {
                    marker = '+';
                } else if (i == activeJobs.size() - 2) {
                    marker = '-';
                }
                String cmd = job.command;
                if (job.status.equals("Done")) {
                    if (cmd.endsWith(" &")) {
                        cmd = cmd.substring(0, cmd.length() - 2).trim();
                    } else if (cmd.endsWith("&")) {
                        cmd = cmd.substring(0, cmd.length() - 1).trim();
                    }
                    toRemove.add(job);
                }
                String statusField = String.format("%-24s", job.status);
                originalOut.println("[" + job.jobNum + "]" + marker + "  " + statusField + cmd);
            }
            activeJobs.removeAll(toRemove);
        }
    }

    private static void reapExitedJobs(java.io.PrintStream originalOut) {
        synchronized (activeJobs) {
            java.util.List<Job> toRemove = new java.util.ArrayList<>();
            for (int i = 0; i < activeJobs.size(); i++) {
                Job job = activeJobs.get(i);
                if (job.process != null && !job.process.isAlive()) {
                    job.status = "Done";
                    
                    char marker = ' ';
                    if (i == activeJobs.size() - 1) {
                        marker = '+';
                    } else if (i == activeJobs.size() - 2) {
                        marker = '-';
                    }
                    
                    String cmd = job.command;
                    if (cmd.endsWith(" &")) {
                        cmd = cmd.substring(0, cmd.length() - 2).trim();
                    } else if (cmd.endsWith("&")) {
                        cmd = cmd.substring(0, cmd.length() - 1).trim();
                    }
                    
                    String statusField = String.format("%-24s", job.status);
                    originalOut.println("[" + job.jobNum + "]" + marker + "  " + statusField + cmd);
                    toRemove.add(job);
                }
            }
            activeJobs.removeAll(toRemove);
        }
    }

    private static int getNextJobNumber() {
        if (activeJobs.isEmpty()) {
            return 1;
        }
        int max = 0;
        for (Job job : activeJobs) {
            if (job.jobNum > max) {
                max = job.jobNum;
            }
        }
        return max + 1;
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

    public static class CommandSpec {
        public final java.util.List<String> args = new java.util.ArrayList<>();
        public String redirectFile = null;
        public String redirectStderrFile = null;
        public boolean appendRedirect = false;
        public boolean appendStderrRedirect = false;
    }

    public static class ParsedCommand {
        public final java.util.List<CommandSpec> commands;
        public final boolean runInBackground;

        public ParsedCommand(java.util.List<CommandSpec> commands, boolean runInBackground) {
            this.commands = commands;
            this.runInBackground = runInBackground;
        }
    }

    private static ParsedCommand parseCommandLine(String input) {
        java.util.List<CommandSpec> commands = new java.util.ArrayList<>();
        CommandSpec currentCmd = new CommandSpec();
        
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean inArg = false;
        boolean expectingRedirectFile = false;
        boolean expectingStderrRedirectFile = false;
        boolean runInBackground = false;

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
                } else if (c == '|') {
                    if (inArg) {
                        if (expectingRedirectFile) {
                            currentCmd.redirectFile = current.toString();
                            expectingRedirectFile = false;
                        } else if (expectingStderrRedirectFile) {
                            currentCmd.redirectStderrFile = current.toString();
                            expectingStderrRedirectFile = false;
                        } else {
                            currentCmd.args.add(current.toString());
                        }
                        current.setLength(0);
                    }
                    commands.add(currentCmd);
                    currentCmd = new CommandSpec();
                    inArg = false;
                } else if (c == '>') {
                    boolean isDouble = (i + 1 < input.length() && input.charAt(i + 1) == '>');
                    if (isDouble) {
                        i++;
                    }
                    if (inArg) {
                        if (current.length() == 1 && current.charAt(0) == '1') {
                            current.setLength(0);
                            expectingRedirectFile = true;
                            currentCmd.appendRedirect = isDouble;
                        } else if (current.length() == 1 && current.charAt(0) == '2') {
                            current.setLength(0);
                            expectingStderrRedirectFile = true;
                            currentCmd.appendStderrRedirect = isDouble;
                        } else {
                            if (expectingRedirectFile) {
                                currentCmd.redirectFile = current.toString();
                                expectingRedirectFile = false;
                            } else if (expectingStderrRedirectFile) {
                                currentCmd.redirectStderrFile = current.toString();
                                expectingStderrRedirectFile = false;
                            } else {
                                currentCmd.args.add(current.toString());
                            }
                            current.setLength(0);
                            expectingRedirectFile = true;
                            currentCmd.appendRedirect = isDouble;
                        }
                    } else {
                        expectingRedirectFile = true;
                        currentCmd.appendRedirect = isDouble;
                    }
                    inArg = false;
                } else if (c == '&') {
                    if (inArg) {
                        if (expectingRedirectFile) {
                            currentCmd.redirectFile = current.toString();
                            expectingRedirectFile = false;
                        } else if (expectingStderrRedirectFile) {
                            currentCmd.redirectStderrFile = current.toString();
                            expectingStderrRedirectFile = false;
                        } else {
                            currentCmd.args.add(current.toString());
                        }
                        current.setLength(0);
                    }
                    runInBackground = true;
                    inArg = false;
                } else if (Character.isWhitespace(c)) {
                    if (inArg) {
                        if (expectingRedirectFile) {
                            currentCmd.redirectFile = current.toString();
                            expectingRedirectFile = false;
                        } else if (expectingStderrRedirectFile) {
                            currentCmd.redirectStderrFile = current.toString();
                            expectingStderrRedirectFile = false;
                        } else {
                            currentCmd.args.add(current.toString());
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
                currentCmd.redirectFile = current.toString();
            } else if (expectingStderrRedirectFile) {
                currentCmd.redirectStderrFile = current.toString();
            } else {
                currentCmd.args.add(current.toString());
            }
        }
        commands.add(currentCmd);
        return new ParsedCommand(commands, runInBackground);
    }
}