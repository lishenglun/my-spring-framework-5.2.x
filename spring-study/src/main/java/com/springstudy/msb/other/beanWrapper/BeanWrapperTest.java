package com.springstudy.msb.other.beanWrapper;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description BeanWrapper的作用：它是一个包装类，提供了一些方法，让我们来方便操作里面的属性值。spring提供的一个小工具类。
 * @date 2022/7/14 3:47 下午
 */
public class BeanWrapperTest {

	public static void main(String[] args) {
		User user = new User();
		BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(user);
		beanWrapper.setPropertyValue("username", "张三");
		// 打印"张三"
		System.out.println(user.getUsername());

		PropertyValue value = new PropertyValue("username", "李四");
		beanWrapper.setPropertyValue(value);
		// 打印"李四"
		System.out.println(user.getUsername());
	}

}

class User {

	private String username;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}
}