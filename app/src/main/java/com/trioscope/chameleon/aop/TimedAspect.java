package com.trioscope.chameleon.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 8/7/15.
 */
@Slf4j
@Aspect
public class TimedAspect {
    private static final String POINTCUT_METHOD =
            "execution(@com.trioscope.chameleon.aop.Timed * *(..))";

    private static final String POINTCUT_CONSTRUCTOR =
            "execution(@com.trioscope.chameleon.aop.Timed *.new(..))";

    @Pointcut(POINTCUT_METHOD)
    public void methodAnnotatedWithDebugTrace() {
    }

    @Pointcut(POINTCUT_CONSTRUCTOR)
    public void constructorAnnotatedDebugTrace() {
    }

    @Around("methodAnnotatedWithDebugTrace() || constructorAnnotatedDebugTrace()")
    public Object weaveJoinPoint(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();

        long startTimeMilli = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long endTimeMilli = System.currentTimeMillis();

        log.info("{}.{} took {}ms", className, methodName, (endTimeMilli - startTimeMilli));

        return result;
    }
}
