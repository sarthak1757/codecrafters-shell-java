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
            if(userinput.equals("exit")) {
                break;
            }
            System.out.println(userinput + ": command not found");
        }
        sc.close();

    }
}