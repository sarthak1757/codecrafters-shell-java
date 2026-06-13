import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        System.out.print("$ ");
        Scanner sc = new Scanner(System.in);
        String userinput = sc.nextLine();
        // String[] Command = userinput.split(" ");
        System.out.println(userinput + ": command not found");
        System.out.print("$");
        sc.close();

    }
}
