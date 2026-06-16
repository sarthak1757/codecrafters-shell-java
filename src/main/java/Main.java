import java.util.Scanner;

public class Main {
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

    private static final java.util.List<String> BUILTINS = java.util.Arrays.asList("exit", "echo", "type");

    private static void executeCommand(String command, String[] parts) {
        if (command.equals("exit")) {
            handleExit(parts);
        } else if (command.equals("echo")) {
            handleEcho(parts);
        } else if (command.equals("type")) {
            handleType(parts);
        } else {
            System.out.println(command + ": command not found");
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
                System.out.println(targetCmd + ": not found");
            }
        }
    }
}