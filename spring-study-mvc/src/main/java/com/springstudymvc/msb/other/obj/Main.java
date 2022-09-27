package com.springstudymvc.msb.other.obj;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 *
 * 测试一下，两个变量指向同一个对象，其中一个变量改变值，另一个变量获取值，是否是获取到改变的值。
 *
 * 结论：它们指向了同一个内存空间，一个人修改值，另一个人去获取值，获取到的是别人修改后的值！
 *
 * @date 2022/7/18 4:12 下午
 */
public class Main {

	public static void main(String[] args) {
		Main main = new Main();
		Person person = new Person();
		person.setName("张三");
		main.test(person);
	}

	public void test(Person person) {
		Person person2 = person;
		System.out.println(person2.getName());
		person.setName("李四");
		System.out.println(person2.getName());
	}

}