package com.ngari.recipe.entity.sign;

import ctd.schema.annotation.Schema;

import javax.persistence.*;
import java.util.Date;

import static javax.persistence.GenerationType.IDENTITY;

@Schema
@Entity
@Table(name = "sign_doctor_ca_info")
public class SignDoctorCaInfo {

    private Integer id;

    /**医生ID*/
    private Integer doctorId;

    /**签名序列号*/
    private String caSerCode;

    /**ca类型*/
    private String caType;

    private Date createDate;

    private Date lastmodify;

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column
    public Integer getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Integer doctorId) {
        this.doctorId = doctorId;
    }

    @Column
    public String getCaSerCode() {
        return caSerCode;
    }

    public void setCaSerCode(String caSerCode) {
        this.caSerCode = caSerCode;
    }

    @Column
    public String getCaType() {
        return caType;
    }

    public void setCaType(String caType) {
        this.caType = caType;
    }

    @Column
    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    @Column
    public Date getLastmodify() {
        return lastmodify;
    }

    public void setLastmodify(Date lastmodify) {
        this.lastmodify = lastmodify;
    }
}
