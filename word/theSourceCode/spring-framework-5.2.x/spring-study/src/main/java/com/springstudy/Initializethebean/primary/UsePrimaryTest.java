package com.springstudy.Initializethebean.primary;

import com.springstudy.config.UsePrimaryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @Primary注解使用测试 结论：A接口，有两个实现类，如果注入A，那么就会报错，因为存在两个bean。此时可以在其中一个实现者上使用@Primary，那么Spring就会默认注入加了@Primary的bean，就不会报错！
 * 参考：https://www.cnblogs.com/yimixiong/p/7524847.html
 */
@Component
public class UsePrimaryTest {

	static class MainTest {
		public static void main(String[] args) {

			AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(UsePrimaryConfig.class);
		}
	}

}