package com.springstudy.aop;

import com.springstudy.aop.aopObject.LandingUser;
import com.springstudy.aop.obj.MerchantInfo;
import com.springstudy.aop.obj.OrderInfo;
import com.springstudy.config.AopConfig;
import com.springstudy.config.ImportAopXmlConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/30 10:24 下午
 */
public class AopTest {

	public static void main(String[] args) {
		aopProxy();
		//aopProxy();
		//xmlBeanParent();
		//testSingleton();
	}


	/**
	 * 测试AbstractBeanFactory#doGetBeanget中的Singleton()
	 */
	public static void testSingleton() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AopConfig.class);
		MerchantInfo bean = context.getBean(MerchantInfo.class);
	}


	/**
	 * 测试：xml中的<bean/>标签的parent属性
	 *
	 * <bean id="orderInfo" class="com.springstudy.aop.obj.OrderInfo">
	 * <property name="name" value="ppppppp"/>
	 * </bean>
	 *
	 * <bean id="orderInfoChild" parent="orderInfo">
	 * <property name="name" value="cccccccccc"/>
	 * </bean>
	 */
	public static void xmlBeanParent() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ImportAopXmlConfig.class);
		OrderInfo orderInfo = context.getBean("orderInfo", OrderInfo.class);
		System.out.println(orderInfo.getName());
		System.out.println("================");
		OrderInfo orderInfoChild = context.getBean("orderInfoChild", OrderInfo.class);
		System.out.println(orderInfoChild.getName());
	}

	/**
	 * aop
	 */
	public static void aopProxy() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AopConfig.class);
		LandingUser landingUser = context.getBean("landingUser", LandingUser.class);
		landingUser.login("zhangsan", "123");
	}

}