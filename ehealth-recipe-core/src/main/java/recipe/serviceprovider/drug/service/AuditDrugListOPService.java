package recipe.serviceprovider.drug.service;

import com.google.common.collect.Lists;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.OrganService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.drug.model.AuditDrugListDTO;
import com.ngari.recipe.drug.model.DrugListBean;
import com.ngari.recipe.drug.service.IAuditDrugListService;
import com.ngari.recipe.entity.*;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.constant.ErrorCode;
import recipe.dao.*;
import recipe.drugsenterprise.YsqRemoteService;
import recipe.util.DrugMatchUtil;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author yinsheng
 * @date 2019\5\16 0016 09:41
 */
@RpcBean("auditDrugListOPService")
public class AuditDrugListOPService implements IAuditDrugListService{

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditDrugListOPService.class);

    @Resource
    private DrugListDAO drugListDAO;

    @Resource
    private AuditDrugListDAO auditDrugListDAO;

    @Resource
    private DrugsEnterpriseDAO drugsEnterpriseDAO;

    @Resource
    private OrganDrugListDAO organDrugListDAO;

    @Resource
    private SaleDrugListDAO saleDrugListDAO;

    @Resource
    private YsqRemoteService ysqRemoteService;

    @Override
    public AuditDrugListDTO getById(Integer auditDrugListId){
        AuditDrugList auditDrugList = auditDrugListDAO.get(auditDrugListId);
        return ObjectCopyUtils.convert(auditDrugList, AuditDrugListDTO.class);
    }

    /**
     * 更新审核信息
     * @param auditDrugId   主键
     * @param status        状态
     * @param rejectReason  拒绝原因
     */
    private void updateAuditDrugListStatus(Integer auditDrugId, Integer status, String rejectReason) {
        auditDrugListDAO.updateAuditDrugListStatus(auditDrugId, status, rejectReason);
    }

    @Override
    public void hospitalAuditDrugList(Integer auditDrugListId, Double salePrice, Integer takeMedicine, Integer status, String rejectReason) {
        AuditDrugList auditDrugList = auditDrugListDAO.get(auditDrugListId);
        if (status == 1) {
            try{
                //表示审核通过
                OrganDrugList organDrugList = organDrugListDAO.get(auditDrugList.getOrganDrugListId());
                organDrugList.setSalePrice(BigDecimal.valueOf(salePrice));
                organDrugList.setTakeMedicine(takeMedicine);
                organDrugListDAO.update(organDrugList);
                auditDrugList.setPrice(salePrice);
                auditDrugList.setStatus(status);
                auditDrugListDAO.update(auditDrugList);

                SaleDrugList saleDrugList = saleDrugListDAO.get(auditDrugList.getSaleDrugListId());
                if (saleDrugList != null) {
                    saleDrugList.setPrice(BigDecimal.valueOf(salePrice));
                    saleDrugListDAO.update(saleDrugList);
                }
                List<DrugsEnterprise> drugsEnterprises = drugsEnterpriseDAO.findAllDrugsEnterpriseByName("岳阳-钥世圈");
                ysqRemoteService.sendAuditDrugList(drugsEnterprises.get(0), auditDrugList.getOrganizeCode(), auditDrugList.getOrganDrugCode(), status);
            }catch (Exception e) {
                LOGGER.info("hospitalAuditDrugList:{}.", auditDrugListId, e);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "审核不成功!");
            }
        } else if (status == 2) {
            //表示审核不通过
            updateAuditDrugListStatus(auditDrugListId, status, rejectReason);
        }
    }

    /**
     * 查询所有审核药品
     * @param start  起始页
     * @param limit  限制页
     * @return       药品列表
     */
    @Override
    public QueryResult<AuditDrugListDTO> findAllDrugList(String drugClass, String keyword ,Integer start, Integer limit) {
        QueryResult result = auditDrugListDAO.findAllDrugList(drugClass, keyword, start, limit);
        result.setItems(covertData(result.getItems()));
        return result;
    }

    private List<AuditDrugListDTO> covertData(List<AuditDrugList> dbList) {
        List<AuditDrugListDTO> newList = Lists.newArrayList();
        AuditDrugListDTO backDTO;
        for (AuditDrugList daod : dbList) {
            backDTO = ObjectCopyUtils.convert(daod, AuditDrugListDTO.class);
            newList.add(backDTO);
        }

        return newList;
    }

    /**
     * 根据机构查询审核药品
     * @param organId  机构ID
     * @param start    起始页
     * @param limit    限制页
     * @return         药品列表
     */
    @Override
    public QueryResult<AuditDrugListDTO> findAllDrugListByOrganId(Integer organId, String drugClass, String keyword ,Integer start, Integer limit) {
        QueryResult result = auditDrugListDAO.findAllDrugListByOrganId(organId, drugClass, keyword, start, limit);
        result.setItems(covertData(result.getItems()));
        return result;
    }

    /**
     * 匹配通用药品目录
     * @param drugName 药品名
     * @return  药品目录
     */
    @Override
    public List<DrugListBean> matchAllDrugListByName(String drugName) {
        String str;
        try{
            str = DrugMatchUtil.match(drugName);
        } catch (Exception e) {
            LOGGER.warn("药品目录匹配分词出现问题,{},{}.", drugName, e.getMessage());
            str = drugName;
        }

        //根据药品名取标准药品库查询相关药品
        List<DrugList> drugLists = null;
        try {
            drugLists = drugListDAO.findBySaleNameOrDrugNameLike(str);
        } catch (Exception e) {
            LOGGER.error("drugMatch:" + e.getMessage());
        }
        return ObjectCopyUtils.convert(drugLists, DrugListBean.class);
    }

    /**
     * 保存药品信息
     * @param auditDrugListId 审核药品目录主键
     * @param drugListId      通用药品目录主键
     */
    @Override
    public void saveAuditDrugListInfo(Integer auditDrugListId, Integer drugListId) {
        if (auditDrugListId == null || drugListId == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "入参不能为空");
        }

        AuditDrugList auditDrugList = auditDrugListDAO.get(auditDrugListId);
        DrugList drugList = drugListDAO.getById(drugListId);
        if (auditDrugList == null || drugList == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "药品不存在");
        }
        try{
            //将该药品保存到机构药品目录
            OrganDrugList organDrugList = packageOrganDrugList(auditDrugList, drugList);
            OrganDrugList resultOrganDrugList = organDrugListDAO.save(organDrugList);

            //将该药品保存到配送药品目录和机构药品目录
            SaleDrugList saleDrugList = packageSaleDrugList(auditDrugList, drugList, resultOrganDrugList);
            SaleDrugList resultSaleDrugList = saleDrugListDAO.save(saleDrugList);

            auditDrugList.setDrugClass(drugList.getDrugClass());
            auditDrugList.setOrganDrugListId(resultOrganDrugList.getOrganDrugId());
            auditDrugList.setSaleDrugListId(resultSaleDrugList.getOrganDrugId());
            auditDrugList.setType(1);
            auditDrugList.setPack(drugList.getPack());
            auditDrugList.setUnit(drugList.getUnit());
            auditDrugList.setDrugType(drugList.getDrugType());
            auditDrugList.setUsingRate(drugList.getUsingRate());
            auditDrugList.setUsePathways(drugList.getUsePathways());
            auditDrugListDAO.update(auditDrugList);
        }catch (Exception e){
            LOGGER.info("saveAuditDrugListInfo:{},{}.", auditDrugListId, drugListId, e);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该机构可能已经匹配过改药品,请更换药品匹配!");
        }

    }

    private SaleDrugList packageSaleDrugList(AuditDrugList auditDrugList, DrugList drugList, OrganDrugList organDrugList) {
        List<DrugsEnterprise> drugsEnterprises = drugsEnterpriseDAO.findAllDrugsEnterpriseByName("岳阳-钥世圈");
        SaleDrugList saleDrugList = new SaleDrugList();
        saleDrugList.setDrugId(drugList.getDrugId());
        saleDrugList.setOrganDrugCode(auditDrugList.getOrganDrugCode());
        saleDrugList.setOrganDrugId(organDrugList.getOrganDrugId());
        saleDrugList.setCreateDt(new Date());
        saleDrugList.setOrganId(drugsEnterprises.get(0).getId());
        saleDrugList.setStatus(1);
        saleDrugList.setPrice(BigDecimal.valueOf(drugList.getPrice1()));
        saleDrugList.setLastModify(new Date());
        return saleDrugList;
    }

    private OrganDrugList packageOrganDrugList(AuditDrugList auditDrugList, DrugList drugList) {
        OrganService organService = BasicAPI.getService(OrganService.class);
        OrganDTO organ = organService.getOrganByOrganizeCode(auditDrugList.getOrganizeCode());

        OrganDrugList organDrugList = new OrganDrugList();
        organDrugList.setDrugId(drugList.getDrugId());
        organDrugList.setOrganDrugCode(auditDrugList.getOrganDrugCode());
        organDrugList.setOrganId(organ.getOrganId());
        organDrugList.setProducerCode("");
        organDrugList.setCreateDt(new Date());
        organDrugList.setStatus(1);
        organDrugList.setSalePrice(BigDecimal.valueOf(drugList.getPrice1()));
        organDrugList.setLastModify(new Date());
        return organDrugList;
    }

    /**
     * 删除审核药品目录
     * @param auditDrugListId  主键
     */
    public void deleteAuditDrugListById(Integer auditDrugListId) {
        AuditDrugList auditDrugList = auditDrugListDAO.get(auditDrugListId);
        if (auditDrugList == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "药品不存在");
        }
        auditDrugList.setStatus(3);
        auditDrugListDAO.update(auditDrugList);
    }
}
