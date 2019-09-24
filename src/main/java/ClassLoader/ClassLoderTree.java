package ClassLoader;

import java.util.Map;
import java.util.Properties;

public class ClassLoderTree {
    public static void main(String[] args) {


        System.out.println(System.getProperty("sun.boot.class.path"));
//        System.out.println(System.getProperty("java.ext.dirs"));
//        System.out.println(System.getProperty("java.class.path"));
        Properties properties = System.getProperties();
        System.out.println(properties);
//        for (Map.Entry<String, String> entry : properties.entrySet()) {
//            System.out.print(entry.getKey() + ":" + entry.getValue());
//            System.out.println();
//        }
    }
}
