package com.springstudy.Initializethebean.ReadTheSourceCode.obj;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Service;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/9/2 9:46 下午
 */
@Service
public class R3 /*implements ImportBeanDefinitionRegistrar*/ {

	public R3(RC4 rc4){
		System.out.println(rc4);
	}


//	public R3(){
//	}


	/*@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(R3.class);
		AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
		beanDefinition.getConstructorArgumentValues().addGenericArgumentValue("com.springstudy.Initializethebean.ReadTheSourceCode.obj.RC4");
		registry.registerBeanDefinition("r3", beanDefinition);
	}*/
}