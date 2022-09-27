package com.springstudy.msb.s_07.selfEditor2;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.factory.config.CustomEditorConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/7/6 10:14 上午
 */
@Configuration
@ComponentScan("com.springstudy.msb.s_07.selfEditor2")
//@PropertySource("classpath:msb/spring-07-customer.properties")
public class SelfEditor2Configuration {

	// ⚠️上面️@PropertySource文件里面的属性值会应用在这个对象里面
	//@Bean
	//public Customer customer() {
	//	return new Customer();
	//}

	@Bean
	public CustomEditorConfigurer customEditorConfigurer() {
		CustomEditorConfigurer customEditorConfigurer = new CustomEditorConfigurer();

		// 方式一：添加属性编辑器的注册器
		customEditorConfigurer.setPropertyEditorRegistrars(new PropertyEditorRegistrar[]{new AddressPropertyEditorRegistrar()});

		// 方式二：直接添加属性编辑器
		//Map<Class<?>, Class<? extends PropertyEditor>> propertyEditorMap = new HashMap<>();
		//propertyEditorMap.put(Address.class, AddressPropertyEditor.class);
		//customEditorConfigurer.setCustomEditors(propertyEditorMap);

		return customEditorConfigurer;
	}

}