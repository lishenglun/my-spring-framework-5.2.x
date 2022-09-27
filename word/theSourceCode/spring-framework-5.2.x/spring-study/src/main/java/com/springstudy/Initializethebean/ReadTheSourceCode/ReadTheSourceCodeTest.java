package com.springstudy.Initializethebean.ReadTheSourceCode;

import com.springstudy.Initializethebean.ReadTheSourceCode.obj.Color;
import com.springstudy.Initializethebean.ReadTheSourceCode.obj.R1;
import com.springstudy.config.ReadTheSourceCodeConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/9/2 9:25 下午
 */
public class ReadTheSourceCodeTest {

	public static void main(String[] args) {
//		readXmlConfig();
		readAnnotationConfig();
	}


	public static void readXmlConfig(){
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring-read-the-source-config.xml");
		//Color color = context.getBean("color", Color.class);
		//System.out.println(color);
		//color.init();
	}

	public static void readAnnotationConfig(){
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ReadTheSourceCodeConfig.class);
		//R1 r1 = context.getBean("r1", R1.class);
		//System.out.println(r1);
	}

}