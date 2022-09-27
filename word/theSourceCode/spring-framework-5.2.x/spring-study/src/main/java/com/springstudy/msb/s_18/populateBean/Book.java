package com.springstudy.msb.s_18.populateBean;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/15 8:35 下午
 */
public class Book {

	private String name;

	private String author;

	private Long price;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public Long getPrice() {
		return price;
	}

	public void setPrice(Long price) {
		this.price = price;
	}
}