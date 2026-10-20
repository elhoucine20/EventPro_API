package com.eventPro.eventPro.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RestControllerAdvice
@RequestMapping("/users")
@Controller
public class testConnexion {

    @GetMapping()
    public String getConnexion(){
        return "connexion avec success";
    }
}
