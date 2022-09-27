package com.springstudy.book2.chapter7_aop.instrument;


// 在java-agent-study工程当中实现，前往java-agent-study工程当中查看


/**
 * 通过java.lang.instrument包实现一个Java agent，使得"监控代码"和"应用代码"完全隔离
 * <p>
 * 也就是通过java.lang.instrument，在类加载时，修改类的字节码，改变一个类，从而实现AOP功能，相当于在JVM层面做了AOP支持；
 */


