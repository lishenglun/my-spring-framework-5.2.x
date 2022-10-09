package com.springstudy.msb.other.typeDescriptor;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/10/8 22:47
 */
public class TypeDescriptorMain {

	public static void main(String[] args) {
		test_isAssignableFrom();
	}

	/**
	 * 测试Class#isAssignableFrom()
	 *
	 * 结论：1、调用者是参数的超类，或者和参数相同，就返回true；2、调用者是参数的子类，则返回false
	 */
	public static void test_isAssignableFrom(){
		// true
		System.out.println(A.class.isAssignableFrom(A.class));
		// true
		// Az extends A
		System.out.println(A.class.isAssignableFrom(Az.class));

		// false
		// A extends B implements C
		System.out.println(A.class.isAssignableFrom(B.class));
		// false
		// A extends B implements C
		System.out.println(A.class.isAssignableFrom(C.class));
	}

}