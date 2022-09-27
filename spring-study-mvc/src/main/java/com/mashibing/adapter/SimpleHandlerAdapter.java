package com.mashibing.adapter;

public class SimpleHandlerAdapter implements HandlerAdapter {
  
  
    @Override
	public void handle(Object handler) {
        ((SimpleController)handler).doSimplerHandler();  
    }  
  
    @Override
	public boolean supports(Object handler) {
        return (handler instanceof SimpleController);  
    }  
  
}  