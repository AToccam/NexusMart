package com.nexusmart.seckill.config.datasource;

public final class DynamicDataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();

    private DynamicDataSourceContextHolder() {
    }

    public static void useMaster() {
        CONTEXT.set(DataSourceType.MASTER);
    }

    public static void useSlave() {
        CONTEXT.set(DataSourceType.SLAVE);
    }

    public static DataSourceType get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
