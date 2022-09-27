package com.springstudy.msb.s_28.tx_annotation;

import com.springstudy.msb.s_28.tx_annotation.config.Transaction;
import com.springstudy.msb.s_28.tx_annotation.service.BookService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/16 5:05 下午
 */
public class Main {

	public static void main(String[] args) {
		// 注解配置的上下文对象
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext();
		ac.register(Transaction.class);
		ac.refresh();
		BookService bookService = ac.getBean(BookService.class);
		bookService.updateBalance("eee",1);
	}

}