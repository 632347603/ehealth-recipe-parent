package com.ngari.recipe.entity;

import ctd.schema.annotation.ItemProperty;
import ctd.schema.annotation.Schema;

import javax.persistence.*;

import static javax.persistence.GenerationType.IDENTITY;

/**
 * 药企配送地址
 * @company: Ngarihealth
 * @author: zhongzixuan
 * @date:2016/6/8.
 */

@Entity
@Schema
@Table(name = "cdr_enterprise_address")
@Access(AccessType.PROPERTY)
public class EnterpriseAddress implements java.io.Serializable{

    private static final long serialVersionUID = 6110497203150534282L;

    @ItemProperty(alias = "药企地址序号")
    private Integer id;

    @ItemProperty(alias = "药企序号")
    private Integer enterpriseId;

    @ItemProperty(alias = "药企配送地址")
    private String address;

    @ItemProperty(alias = "配送地址状态")
    private Integer status;

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "Id", unique = true, nullable = false)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(name = "EnterpriseId", nullable = false)
    public Integer getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(Integer enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    @Column(name = "Address", nullable = false)
    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Column(name = "Status", nullable = false)
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
