package Thread;

public class ThreadTest {
    public static void main(String[] args) {
//        LiftOff liftOff = new LiftOff();
//        liftOff.run();
        for (int i = 0; i < 5; i++) {
            new Thread(new LiftOff()).start();
        }

        System.out.println("Waiting for LiftOff!");
    }
}
