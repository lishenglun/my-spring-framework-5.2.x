package com.springstudy.msb.s_10.condition;

import com.springstudy.msb.s_10.Person;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/26 9:31 上午
 */
@Conditional({WindowsCondition.class})
@Configuration
public class BeanConfig {

	// 解析我们所对应的被注解修饰的BD

	@Bean(name = "bill")
	public Person person1() {
		return new Person("Bill Gates", 62);
	}

	@Conditional({LinuxCondition.class})
	@Bean(name = "linus")
	public Person person2() {
		return new Person("Linus", 48);
	}

}