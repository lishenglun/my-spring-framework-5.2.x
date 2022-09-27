package com.springstudymvc.msb.other.test_AnnotatedElementUtils;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/2 11:28 上午
 */
@Li
@Service
@Configuration
public class Ha {

	public static void main(String[] args) {
		Li mergedAnnotation = AnnotatedElementUtils.findMergedAnnotation/* 查找注解，并合并 */(Ha.class, Li.class);
		assert mergedAnnotation != null;
		String value = mergedAnnotation.value();
		System.out.println(value);
	}

}