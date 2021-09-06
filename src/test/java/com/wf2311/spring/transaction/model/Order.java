package com.wf2311.spring.transaction.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 16:35.
 */
@Entity
@Table(name = "t_order")
@Data
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private float price;

    private LocalDateTime createdOn;

    private Integer createdBy;
}
