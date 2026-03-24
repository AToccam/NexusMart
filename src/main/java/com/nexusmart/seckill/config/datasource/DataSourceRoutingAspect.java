package com.nexusmart.seckill.config.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataSourceRoutingAspect {

    @Around("execution(* com.nexusmart.seckill.service..*(..))")
    public Object route(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();

        boolean forceMaster = method.isAnnotationPresent(WriteDataSource.class)
                || targetClass.isAnnotationPresent(WriteDataSource.class);
        boolean forceSlave = method.isAnnotationPresent(ReadOnlyDataSource.class)
                || targetClass.isAnnotationPresent(ReadOnlyDataSource.class);

        if (forceMaster) {
            DynamicDataSourceContextHolder.useMaster();
        } else if (forceSlave || isReadMethod(method.getName())) {
            DynamicDataSourceContextHolder.useSlave();
        } else {
            DynamicDataSourceContextHolder.useMaster();
        }

        try {
            return joinPoint.proceed();
        } finally {
            DynamicDataSourceContextHolder.clear();
        }
    }

    private boolean isReadMethod(String methodName) {
        String lower = methodName.toLowerCase();
        return lower.startsWith("get")
                || lower.startsWith("list")
                || lower.startsWith("find")
                || lower.startsWith("query")
                || lower.startsWith("select")
                || lower.startsWith("count");
    }
}
