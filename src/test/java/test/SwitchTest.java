package test;

public class SwitchTest {
    public static void main(String[] args) {
        String a = "h";
        switch (a) {
            case "hh":
                System.out.println("===");
            default:
                System.out.println("----");
                if ( a.length() < 2  || "hh".equals(a.substring(0, 2)) ){
                    break;
                }
                System.out.println("----");
        }

        System.out.println("sdfsf".substring(20));
    }
}
