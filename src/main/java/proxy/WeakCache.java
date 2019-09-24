package proxy;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

final class WeakCache<K, P, V> {

    // 引用队列，被 GC 回收的引用将被放在引用队列中
    private final ReferenceQueue<K> refQueue = new ReferenceQueue<>();
    // map 是一个缓存 key 为 CacheKey, value 为 ConcurrentHashMap<Object, Supplier> 二级缓存
    // ConcurrentHashMap<Object, Supplier> 是一个二级缓存，key 为 subKeyFactory 生成的对象, value 为一个实现了接口 Supplier<V> 的 Factory 或 CacheValue<V>
    // 这里的 key 为 Object 是为了支持当 k 为空时，CacheKey.valueOf 返回的 NULL_KEY 为 Object
    private final ConcurrentHashMap<Object, ConcurrentHashMap<Object, Supplier<V>>> map = new ConcurrentHashMap<>();
    //
    private final ConcurrentHashMap<Supplier<V>, Boolean> reverseMap = new ConcurrentHashMap<>();

    // 生成二级缓存key的工厂
    private final BiFunction<K, P, ?> subKeyFactory;
    //生成二级缓存value的工厂
    private final BiFunction<K, P, V> valueFactory;

    public WeakCache(BiFunction<K, P, ?> subKeyFactory, BiFunction<K, P, V> valueFactory) {
        this.subKeyFactory = Objects.requireNonNull(subKeyFactory);
        this.valueFactory = Objects.requireNonNull(valueFactory);
    }

//    public boolean containsValue(V value) {
//        Objects.requireNonNull(value);
//
//        expungeStaleEntries();
//        return reverseMap.containsKey(new LookupValue<>(value));
//    }

    /**
     * Returns the current number of cached entries that
     * can decrease over time when keys/values are GC-ed.
     */
    public int size() {
        expungeStaleEntries();
        return reverseMap.size();
    }

    private void expungeStaleEntries() {
        CacheKey<K> cacheKey;
        while ((cacheKey = (CacheKey<K>)refQueue.poll()) != null) {
            cacheKey.expungeFrom(map, reverseMap);
        }
    }

    public V get(K key, P parameter) {
        Objects.requireNonNull(parameter);
        expungeStaleEntries();

        Object cacheKey = CacheKey.valueOf(key, refQueue);

        //  保证相同的 key 从 Map<Key, Value> 中拿出同一个 Value 对象
        //  单线程满足要求，多线程不满足（判断 value == null 时，其他线程可能会进行 put 操作）
        //  Map<Key, Value> map = new HashMap<>();
        //  Key key = new Key();
        //  Value value = map.get(key);
        //  if (value == null) {
        //      value = new Value();
        //      map.put(key, value);
        //  }
        //  使用 ConCurrentHashMap 来进行
        //  ConCurrentHashMap<Key, Value> map = new ConCurrentHashMap<>();
        //  Key key = new Key();
        //  Value value = map.get(key);
        //  if (value == null) {
        //      Value oldValue = map.putIfAbsent(key, value = new Value());
        //      oldValue 不为空时，说明有其他线程进行了 put 操作
        //      if (oldValue != null) {
        //          value = oldValue;
        //      }
        //  }
        ConcurrentHashMap<Object, Supplier<V>> valuesMap = map.get(cacheKey);
        if (valuesMap == null) {
            ConcurrentHashMap<Object, Supplier<V>> oldValuesMap = map.putIfAbsent(cacheKey, valuesMap = new ConcurrentHashMap<>());
            if (oldValuesMap != null) {
                valuesMap = oldValuesMap;
            }
        }

        Object subKey = Objects.requireNonNull(subKeyFactory.apply(key, parameter));
        Supplier<V> supplier = valuesMap.get(subKey);
        Factory factory = null;
        while (true) {
            if (supplier != null) {
                V value = supplier.get();
                if (value != null) {
                    return value;
                }
            }
            if (factory == null) {
                factory = new Factory(key, parameter, subKey, valuesMap);
            }

            if (supplier == null) {
                // 如果 subKey 没有对应的值，将 factory 放入二级缓存
                supplier = valuesMap.putIfAbsent(subKey, factory);
                if (supplier == null) {
                    // 将 factory 成功放入缓存
                    supplier = factory;
                }
                // 其他线程修改了 subKey 对应的值，继续下一个循环
            } else {
                // 如果 supplier 不为空，但是 supplier.get() 为空，替换 supplier
                if (valuesMap.replace(subKey, supplier, factory)) {
                    // 成功将 factory 替换成新的值
                    supplier = factory;
                } else {
                    supplier = valuesMap.get(subKey);
                }
            }
        }
    }

    /**
     *
     */
    private final class Factory implements Supplier<V> {
        private final K key;
        private final P parameter;
        private final Object subKey;
        private final ConcurrentHashMap<Object, Supplier<V>> valuesMap;

        public Factory(K key, P parameter, Object subKey, ConcurrentHashMap<Object, Supplier<V>> valuesMap) {
            this.key = key;
            this.parameter = parameter;
            this.subKey = subKey;
            this.valuesMap = valuesMap;
        }

        @Override
        public synchronized V get() {
            // re-check
            //这里再一次去二级缓存里面获取 supplier, 用来验证是否是 Factory 本身
            Supplier<V> supplier = valuesMap.get(subKey);
            if (supplier != this) {
                // something changed while we were waiting:
                // might be that we were replaced by a CacheValue
                // or were removed because of failure ->
                // return null to signal WeakCache.get() to retry
                // the loop
                return null;
            }
            // else still us (supplier == this)

            // create new value
            V value = null;
            try {
                // 委托 valueFactory 去生成代理类
                value = Objects.requireNonNull(valueFactory.apply(key, parameter));
            } finally {
                // 如果生成代理类失败, 就将这个二级缓存删除
                if (value == null) { // remove us on failure
                    valuesMap.remove(subKey, this);
                }
            }
            // the only path to reach here is with non-null value
            assert value != null;

            // wrap value with CacheValue (WeakReference)
            // 使用弱引用包装生成的代理类
            CacheValue<V> cacheValue = new CacheValue<>(value);

            // put into reverseMap
            reverseMap.put(cacheValue, Boolean.TRUE);

            // try replacing us with CacheValue (this should always succeed)
            if (!valuesMap.replace(subKey, this, cacheValue)) {
                throw new AssertionError("Should not reach here");
            }

            // successfully replaced us with new CacheValue -> return the value
            // wrapped by it
            return value;
        }
    }

    private interface Value<V> extends Supplier<V> {}

    private static final class CacheValue<V> extends WeakReference<V> implements Value<V> {
        private final int hash;

        private CacheValue(V value) {
            super(value);
            this.hash = System.identityHashCode(value);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            V value;
            return obj == this
                    || obj instanceof Value
                    // cleared CacheValue is only equal to itself
                    && (value = get()) != null
                    && value == ((Value<?>) obj).get(); // compare by identity
        }
    }

    private static final class CacheKey<K> extends WeakReference<K> {
        private static final Object NULL_KEY = new Object();

        private final int hash;

        private CacheKey(K key, ReferenceQueue<K> refQueue) {
            super(key, refQueue);
            this.hash = System.identityHashCode(key);
        }

        static <K> Object valueOf(K key, ReferenceQueue<K> refQueue) {
            return key == null ? NULL_KEY : new CacheKey<>(key, refQueue);
        }

        void expungeFrom(ConcurrentHashMap<?, ? extends ConcurrentHashMap<?, ?>> map,
                        ConcurrentHashMap<?, Boolean> reverseMap) {

        }

        @Override
        public boolean equals(Object obj) {
            K key;
            return obj == this
                    || obj != null
                    && obj.getClass() == this.getClass()
                    && (key = this.get()) != null
                    && key == ((CacheKey<K>) obj).get();
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
