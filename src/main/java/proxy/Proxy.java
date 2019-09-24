package proxy;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.function.BiFunction;

public class Proxy implements java.io.Serializable {
    private static final long serialVersionUID = -2222568056686623797L;

    private static final WeakCache<ClassLoader, Class<?>[], Class<?>> proxyClassCache = new WeakCache<>(new KeyFactory(), null);

    private static final Object key0 = new Object();

    private static final class Key1 extends WeakReference<Class<?>> {
        private final int hash;
        Key1(Class<?> referent) {
            super(referent);
            this.hash = referent.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            Class<?> intf;
            return this == obj
                    || obj != null
                    && obj.getClass() == Key1.class
                    && (intf = get()) != null
                    && intf == ((Key1) obj).get();
        }
    }

    private static final class Key2 extends WeakReference<Class<?>> {
        private final int hash;
        private final WeakReference<Class<?>> ref2;

        Key2(Class<?> interface1, Class<?> interface2) {
            super(interface1);
            this.hash = 31 * interface1.hashCode() + interface2.hashCode();
            ref2 = new WeakReference<>(interface2);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            Class<?> intf1, intf2;
            return this == obj
                    || obj != null
                    && obj.getClass() == Key2.class
                    && (intf1 = get()) != null
                    && intf1 == ((Key2) obj).get()
                    && (intf2 = ref2.get()) != null
                    && intf2 == ((Key2) obj).ref2.get();
        }
    }

    private static final class KeyX {
        private final int hash;
        private final WeakReference<Class<?>>[] refs;

        KeyX(Class<?>[] interfaces) {
            this.hash = Arrays.hashCode(interfaces);
//            refs = new WeakReference<Class<?>> [interfaces.length];   TODO why not?
            refs = (WeakReference<Class<?>>[])new WeakReference<?>[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
                refs[i] = new WeakReference<>(interfaces[i]);
            }
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj
                    || obj != null
                    && obj.getClass() == KeyX.class
                    && equals(refs, ((KeyX) obj).refs);
        }

        private static boolean equals(WeakReference<Class<?>>[] refs1, WeakReference<Class<?>>[] refs2) {
            if (refs1.length != refs2.length) {
                return false;
            }
            for (int i = 0; i < refs1.length; i++) {
                Class<?> intf = refs1[i].get();
                if (intf == null || intf != refs2[i].get()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class KeyFactory implements BiFunction<ClassLoader, Class<?>[], Object> {
        @Override
        public Object apply(ClassLoader classLoader, Class<?>[] interfaces) {
            switch (interfaces.length) {
                case 1: return new Key1(interfaces[0]);
                case 2: return new Key2(interfaces[0], interfaces[1]);
                case 0: return key0;
                default: return new KeyX(interfaces);
            }
        }
    }

    private static final class ProxyClassFactory implements BiFunction<ClassLoader, Class<?>[], Class<?>> {
        private static final String proxyClassNamePrefix = "$Proxy";

        @Override
        public Class<?> apply(ClassLoader classLoader, Class<?>[] classes) {
            return null;
        }
    }
}
