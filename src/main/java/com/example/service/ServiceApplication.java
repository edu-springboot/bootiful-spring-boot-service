package com.example.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@SpringBootApplication
public class ServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> readyEventApplicationListener(CustomerService cs) {
        return event -> cs.all().forEach(System.out::println);
    }

}

@Controller
@ResponseBody
class CustomerHttpController {
    private final CustomerService service;
    private final ObservationRegistry registry;

    public CustomerHttpController(CustomerService service, ObservationRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @GetMapping("/customers")
    Collection<Customer> all() {
        return this.service.all();
    }

    @GetMapping("/customers/{name}")
    Customer byName(@PathVariable String name) {
        Assert.state(Character.isUpperCase(name.charAt(0)), "The name must start with a capital letter!");
        return Observation
                .createNotStarted("byName", this.registry)
                .observe(() -> this.service.byName(name));
    }


//    @GetMapping("/customers/{id}")
//    Customer byId(@PathVariable Integer id) {
//        return this.service.byId(id);
//    }

}

@ControllerAdvice
class ErrorHandlingControllerAdvice {
    @ExceptionHandler
    public ProblemDetail handleIllegalStateException(IllegalStateException exception) {
        var pd = ProblemDetail.forStatus(HttpStatusCode.valueOf(404));
        pd.setDetail("The name must start with a capital letter!");
        return pd;
    }
}

@Service
class CustomerService {
    private final JdbcTemplate template;

    private final RowMapper<Customer> customerRowMapper =
            (rs, rowNum) -> new Customer(rs.getInt("id"), rs.getString("name"));

    public CustomerService(JdbcTemplate template) {
        this.template = template;
    }

    Customer byId(Integer id) {
        return this.template.queryForObject("select * from customers where id =?",
                this.customerRowMapper, id);
    }

    Customer byName(String name) {
        return this.template.queryForObject("select * from customers where name =?",
                this.customerRowMapper, name);
    }

    Collection<Customer> all() {
        return this.template.query("select * from customers",
                this.customerRowMapper);
    }
}

record Customer(Integer id, String name) {

}
