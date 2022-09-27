package com.springstudymvc.msb.mvc_10;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class LianException extends RuntimeException{

    private static final long serialVersionUID = -7876639844077632100L;

}
