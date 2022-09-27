package com.springstudymvc.msb.other.test_AnnotatedElementUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/2 11:27 上午
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Li {

	String value() default "小猪佩奇";

}