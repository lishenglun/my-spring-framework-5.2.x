package com.springstudy.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 模拟mybatis的@Select，并不存在真实功能
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Select {

	String value();
}