package com.ngari.recipe.recipe.model;

import ctd.schema.annotation.Dictionary;
import ctd.schema.annotation.ItemProperty;
import ctd.schema.annotation.Schema;

import javax.persistence.*;
import java.io.Serializable;

import static javax.persistence.GenerationType.IDENTITY;

/**
 * @author Created by liuxiaofeng on 2020/12/7.
 * 配送订单导出 处方实体
 */
@Entity
@Schema
@Access(AccessType.PROPERTY)
public class RecipeExportDTO implements Serializable {
    private static final long serialVersionUID = 8970764712165652375L;

    @ItemProperty(alias = "处方序号")
    private Integer recipeId;
    @ItemProperty(alias = "主索引")
    private String mpiid;
    @ItemProperty(alias = "开方机构名称")
    private String organName;
    @ItemProperty(alias = "开方机构")
    private Integer clinicOrgan;
    @ItemProperty(alias = "开方科室")
    @Dictionary(id = "eh.base.dictionary.Depart")
    private Integer depart;
    @ItemProperty(alias = "开方医生")
    @Dictionary(id = "eh.base.dictionary.Doctor")
    private Integer doctor;
    @ItemProperty(alias = "机构疾病名称")
    private String organDiseaseName;
    @ItemProperty(alias = "发药方式")
    @Dictionary(id = "eh.cdr.dictionary.GiveMode")
    private Integer giveMode;
    @ItemProperty(alias = "处方状态")
    @Dictionary(id = "eh.cdr.dictionary.RecipeStatus")
    private Integer status;
    @ItemProperty(alias = "来源标志")
    @Dictionary(id = "eh.cdr.dictionary.FromFlag")
    private Integer fromflag;
    @ItemProperty(alias = "医生姓名")
    private String doctorName;
    @ItemProperty(alias = "患者姓名")
    private String patientName;
    @ItemProperty(alias = "患者医院病历号")
    private String patientID;
    @ItemProperty(alias = "处方类型")
    @Dictionary(id = "eh.cdr.dictionary.RecipeType")
    private Integer recipeType;
    @ItemProperty(alias = "发药药师")
    private String giveUser;
    @Dictionary(id = "eh.cdr.dictionary.BussSourceType")
    private Integer bussSource;
    @ItemProperty(alias = "处方单号")
    private String recipeCode;
    @ItemProperty(alias = "处方业务类型  1 门诊处方  2  复诊处方  3 其他处方")
    private Integer recipeBusinessType;
    @ItemProperty(alias = "煎法")
    private String decoctionId;
    @ItemProperty(alias = "煎法text")
    private String decoctionText;

    @Column(name = "decoctionText")
    public String getDecoctionText() {
        return decoctionText;
    }

    public void setDecoctionText(String decoctionText) {
        this.decoctionText = decoctionText;
    }

    @Column(name = "decoctionId")
    public String getDecoctionId() {
        return decoctionId;
    }

    public void setDecoctionId(String decoctionId) {
        this.decoctionId = decoctionId;
    }

    @Column(name = "clinicOrgan")
    public Integer getClinicOrgan() {
        return clinicOrgan;
    }

    public void setClinicOrgan(Integer clinicOrgan) {
        this.clinicOrgan = clinicOrgan;
    }

    @Column(name = "doctorName")
    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    @Column(name = "patientName")
    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "RecipeID", unique = true, nullable = false)
    public Integer getRecipeId() {
        return this.recipeId;
    }

    public void setRecipeId(Integer recipeId) {
        this.recipeId = recipeId;
    }

    @Column(name = "MPIID", nullable = false)
    public String getMpiid() {
        return this.mpiid;
    }

    public void setMpiid(String mpiid) {
        this.mpiid = mpiid;
    }

    @Column(name = "PatientID")
    public String getPatientID() {
        return patientID;
    }

    public void setPatientID(String patientID) {
        this.patientID = patientID;
    }

    @Column(name = "organName")
    public String getOrganName() {
        return organName;
    }

    public void setOrganName(String organName) {
        this.organName = organName;
    }

    @Column(name = "Depart")
    public Integer getDepart() {
        return this.depart;
    }

    public void setDepart(Integer depart) {
        this.depart = depart;
    }

    @Column(name = "Doctor")
    public Integer getDoctor() {
        return this.doctor;
    }

    public void setDoctor(Integer doctor) {
        this.doctor = doctor;
    }

    @Column(name = "OrganDiseaseName")
    public String getOrganDiseaseName() {
        return this.organDiseaseName;
    }

    public void setOrganDiseaseName(String organDiseaseName) {
        this.organDiseaseName = organDiseaseName;
    }

    @Column(name = "GiveMode")
    public Integer getGiveMode() {
        return giveMode;
    }

    public void setGiveMode(Integer giveMode) {
        this.giveMode = giveMode;
    }

    @Column(name = "Status")
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Column(name = "fromflag")
    public Integer getFromflag() {
        return fromflag;
    }

    public void setFromflag(Integer fromflag) {
        this.fromflag = fromflag;
    }

    public Integer getRecipeType() {
        return recipeType;
    }

    public void setRecipeType(Integer recipeType) {
        this.recipeType = recipeType;
    }

    public String getGiveUser() {
        return giveUser;
    }

    public void setGiveUser(String giveUser) {
        this.giveUser = giveUser;
    }

    public Integer getBussSource() {
        return bussSource;
    }

    public void setBussSource(Integer bussSource) {
        this.bussSource = bussSource;
    }

    public String getRecipeCode() {
        return recipeCode;
    }

    public void setRecipeCode(String recipeCode) {
        this.recipeCode = recipeCode;
    }

    public Integer getRecipeBusinessType() {
        return recipeBusinessType;
    }

    public void setRecipeBusinessType(Integer recipeBusinessType) {
        this.recipeBusinessType = recipeBusinessType;
    }
}
