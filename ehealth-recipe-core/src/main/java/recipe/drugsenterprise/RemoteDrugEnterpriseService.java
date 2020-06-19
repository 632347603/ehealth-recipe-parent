package recipe.drugsenterprise;

import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.service.IRecipeEnterpriseService;
import com.ngari.patient.dto.DepartmentDTO;
import com.ngari.patient.dto.DoctorDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.DepartmentService;
import com.ngari.patient.service.DoctorService;
import com.ngari.patient.service.PatientService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.platform.recipe.mode.*;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.hisprescription.model.HospitalRecipeDTO;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.ApplicationUtils;
import recipe.bean.DrugEnterpriseResult;
import recipe.constant.ParameterConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.dao.*;
import recipe.service.common.RecipeCacheService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ctd.util.AppContextHolder.getBean;

/**
 * 业务使用药企对接类，具体实现在CommonRemoteService
 * company: ngarihealth
 *
 * @author: 0184/yu_yun
 * @date:2017/3/7.
 */
@RpcBean("remoteDrugEnterpriseService")
public class RemoteDrugEnterpriseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDrugEnterpriseService.class);

    private static final String COMMON_SERVICE = "commonRemoteService";

    /**
     * 推送处方
     *
     * @param recipeId 处方ID集合
     * @return
     */
    @RpcService
    public DrugEnterpriseResult pushSingleRecipeInfo(Integer recipeId) {
        DrugEnterpriseResult result = getServiceByRecipeId(recipeId);
        DrugsEnterprise enterprise = result.getDrugsEnterprise();
        RecipeParameterDao parameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        String drugsEnterpriseList = parameterDao.getByName("drugsEnterpriseList");
        if (StringUtils.isNotEmpty(drugsEnterpriseList) && enterprise != null && hasDep(enterprise.getCallSys(), drugsEnterpriseList)) {
            //药企对应的service为空，则通过前置机进行推送
            IRecipeEnterpriseService recipeEnterpriseService = AppContextHolder.getBean("his.iRecipeEnterpriseService",IRecipeEnterpriseService.class);
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            Recipe recipe = recipeDAO.getByRecipeId(recipeId);
            PushRecipeAndOrder pushRecipeAndOrder = getPushRecipeAndOrder(recipe, enterprise);
            HisResponseTO responseTO = recipeEnterpriseService.pushSingleRecipeInfo(pushRecipeAndOrder);
            if (responseTO.isSuccess()) {
                result.setCode(1);
            } else {
                result.setCode(0);
            }
        } else {
            if (DrugEnterpriseResult.SUCCESS.equals(result.getCode()) && null != result.getAccessDrugEnterpriseService()) {
                result = result.getAccessDrugEnterpriseService().pushRecipeInfo(Collections.singletonList(recipeId), enterprise);
                if (DrugEnterpriseResult.SUCCESS.equals(result.getCode())) {
                    result.setDrugsEnterprise(enterprise);
                }
            }
        }
        LOGGER.info("pushSingleRecipeInfo recipeId:{}, result:{}", recipeId, JSONUtils.toString(result));
        return result;
    }

    private PushRecipeAndOrder getPushRecipeAndOrder(Recipe recipe, DrugsEnterprise enterprise) {
        PushRecipeAndOrder pushRecipeAndOrder = new PushRecipeAndOrder();
        //设置处方信息
        pushRecipeAndOrder.setRecipeBean(ObjectCopyUtils.convert(recipe, RecipeBean.class));
        //设置订单信息
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
        pushRecipeAndOrder.setRecipeOrderBean(ObjectCopyUtils.convert(recipeOrder, RecipeOrderBean.class));
        //设置药品详情
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipe.getRecipeId());
        List<PushDrugListBean> pushDrugListBeans = new ArrayList<>();
        //设置配送药品信息
        for (Recipedetail recipedetail : recipedetails) {
            PushDrugListBean pushDrugListBean = new PushDrugListBean();
            SaleDrugList saleDrugList = saleDrugListDAO.getByDrugIdAndOrganId(recipedetail.getDrugId(), enterprise.getId());
            if (saleDrugList != null) {
                pushDrugListBean.setSaleDrugListDTO(ObjectCopyUtils.convert(saleDrugList, SaleDrugListDTO.class));
            }
            pushDrugListBean.setRecipeDetailBean(ObjectCopyUtils.convert(recipedetail, RecipeDetailBean.class));
            pushDrugListBeans.add(pushDrugListBean);
        }
        pushRecipeAndOrder.setPushDrugListBeans(pushDrugListBeans);

        //设置医生信息
        DoctorService doctorService = BasicAPI.getService(DoctorService.class);
        DoctorDTO doctorDTO = doctorService.getByDoctorId(recipe.getDoctor());
        pushRecipeAndOrder.setDoctorDTO(doctorDTO);
        //设置患者信息
        PatientService patientService = BasicAPI.getService(PatientService.class);
        PatientDTO patientDTO = patientService.get(recipe.getMpiid());
        pushRecipeAndOrder.setPatientDTO(patientDTO);
        //设置科室信息
        DepartmentService departmentService = BasicAPI.getService(DepartmentService.class);
        DepartmentDTO departmentDTO = departmentService.get(recipe.getDepart());
        pushRecipeAndOrder.setDepartmentDTO(departmentDTO);
        LOGGER.info("getPushRecipeAndOrder pushRecipeAndOrder:{}.", JSONUtils.toString(pushRecipeAndOrder));
        return pushRecipeAndOrder;
    }

    /**
     * 根据药企推送处方
     *
     * @param drugsEnterprise 药企
     * @return
     */
    @RpcService
    public DrugEnterpriseResult pushSingleRecipe(HospitalRecipeDTO hospitalRecipeDTO, DrugsEnterprise drugsEnterprise) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        result.setAccessDrugEnterpriseService(this.getServiceByDep(drugsEnterprise));
        if (DrugEnterpriseResult.SUCCESS.equals(result.getCode()) && null != result.getAccessDrugEnterpriseService()) {
            result = result.getAccessDrugEnterpriseService().pushRecipe(hospitalRecipeDTO, drugsEnterprise);
            if (DrugEnterpriseResult.SUCCESS.equals(result.getCode())) {
                result.setDrugsEnterprise(drugsEnterprise);
            }
        }
        LOGGER.info("pushSingleRecipeInfo drugsEnterpriseName:{}, result:{}", drugsEnterprise.getName(), JSONUtils.toString(result));
        return result;
    }

    /**
     * 带药企ID进行推送
     *
     * @param recipeId
     * @param depId
     * @return
     */
    @RpcService
    public DrugEnterpriseResult pushSingleRecipeInfoWithDepId(Integer recipeId, Integer depId) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        DrugsEnterprise dep = null;
        if (null != depId) {
            dep = drugsEnterpriseDAO.get(depId);
            if (null != dep) {
                result.setAccessDrugEnterpriseService(this.getServiceByDep(dep));
            } else {
                result.setCode(DrugEnterpriseResult.FAIL);
                result.setMsg("药企" + depId + "未找到");
            }
        } else {
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setMsg("处方单" + recipeId + "未分配药企");
        }

        if (DrugEnterpriseResult.SUCCESS.equals(result.getCode()) && null != result.getAccessDrugEnterpriseService()) {
            result = result.getAccessDrugEnterpriseService().pushRecipeInfo(Collections.singletonList(recipeId), dep);
        }
        LOGGER.info("pushSingleRecipeInfoWithDepId recipeId:{}, result:{}", recipeId, JSONUtils.toString(result));
        return result;
    }

    /**
     * 库存检验
     *
     * @param recipeId        处方ID
     * @param drugsEnterprise 药企
     * @return
     */
    @RpcService
    public boolean scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise) {
        LOGGER.info("scanStock recipeId:{}, drugsEnterprise:{}", recipeId, JSONUtils.toString(drugsEnterprise));
        if (drugsEnterprise != null && drugsEnterprise.getCheckInventoryFlag() != null && drugsEnterprise.getCheckInventoryFlag() == 0) {
            return true;
        }
        DrugEnterpriseResult result = DrugEnterpriseResult.getFail();
        RecipeParameterDao parameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        String drugsEnterpriseList = parameterDao.getByName("drugsEnterpriseList");
        if (StringUtils.isNotEmpty(drugsEnterpriseList) && drugsEnterprise != null && hasDep(drugsEnterprise.getCallSys(), drugsEnterpriseList)) {
            //通过前置机调用
            IRecipeEnterpriseService recipeEnterpriseService = AppContextHolder.getBean("his.iRecipeEnterpriseService",IRecipeEnterpriseService.class);
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            Recipe recipe = recipeDAO.getByRecipeId(recipeId);
            ScanRequestBean scanRequestBean = getScanRequestBean(recipe, drugsEnterprise);
            HisResponseTO responseTO = recipeEnterpriseService.scanStock(scanRequestBean);
            if (responseTO.isSuccess()) {
                return true;
            } else {
                return false;
            }
        }
        AccessDrugEnterpriseService drugEnterpriseService = null;
        if (null == drugsEnterprise) {
            //药企对象为空，则通过处方id获取相应药企实现
            DrugEnterpriseResult result1 = this.getServiceByRecipeId(recipeId);
            if (DrugEnterpriseResult.SUCCESS.equals(result1.getCode())) {
                drugEnterpriseService = result1.getAccessDrugEnterpriseService();
                drugsEnterprise = result1.getDrugsEnterprise();
            }
        } else {
            drugEnterpriseService = this.getServiceByDep(drugsEnterprise);
        }

        if (null != drugEnterpriseService) {
            result = drugEnterpriseService.scanStock(recipeId, drugsEnterprise);
        }
        LOGGER.info("scanStock recipeId:{}, result:{}", recipeId, JSONUtils.toString(result));
        return result.getCode().equals(DrugEnterpriseResult.SUCCESS) ? true : false;
    }

    private ScanRequestBean getScanRequestBean(Recipe recipe, DrugsEnterprise drugsEnterprise) {
        ScanRequestBean scanRequestBean = new ScanRequestBean();
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipe.getRecipeId());
        List<ScanDrugListBean> scanDrugListBeans = new ArrayList<>();
        for (Recipedetail recipedetail : recipedetails) {
            ScanDrugListBean scanDrugListBean = new ScanDrugListBean();
            SaleDrugList saleDrugList = saleDrugListDAO.getByDrugIdAndOrganId(recipedetail.getDrugId(), drugsEnterprise.getId());
            if (saleDrugList != null) {
                scanDrugListBean.setDrugCode(saleDrugList.getOrganDrugCode());
                scanDrugListBean.setTotal(recipedetail.getUseTotalDose().toString());
                scanDrugListBean.setUnit(recipedetail.getDrugUnit());
                scanDrugListBeans.add(scanDrugListBean);
            }
        }
        scanRequestBean.setDrugsEnterpriseBean(ObjectCopyUtils.convert(drugsEnterprise, DrugsEnterpriseBean.class));
        scanRequestBean.setScanDrugListBeans(scanDrugListBeans);
        scanRequestBean.setOrganId(recipe.getClinicOrgan());
        LOGGER.info("getScanRequestBean scanRequestBean:{}.", JSONUtils.toString(scanRequestBean));
        return scanRequestBean;
    }

    @RpcService
    public String getDrugInventory(Integer depId, Integer drugId, Integer organId){
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        LOGGER.info("getDrugInventory depId:{}, drugId:{}", depId, drugId);
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        DrugsEnterprise drugsEnterprise = drugsEnterpriseDAO.getById(depId);
        result.setAccessDrugEnterpriseService(this.getServiceByDep(drugsEnterprise));
        if (DrugEnterpriseResult.SUCCESS.equals(result.getCode()) && null != result.getAccessDrugEnterpriseService()) {
           return  result.getAccessDrugEnterpriseService().getDrugInventory(drugId, drugsEnterprise, organId);
        } else {
            return "0";
        }
    }


    /**
     * 药师审核通过通知消息
     *
     * @param recipeId  处方ID
     * @param checkFlag 审核结果
     * @return
     */
    @RpcService
    public DrugEnterpriseResult pushCheckResult(Integer recipeId, Integer checkFlag) {
        DrugEnterpriseResult result = getServiceByRecipeId(recipeId);
        if (DrugEnterpriseResult.SUCCESS.equals(result.getCode()) && null != result.getAccessDrugEnterpriseService()) {
            result = result.getAccessDrugEnterpriseService().pushCheckResult(recipeId, checkFlag, result.getDrugsEnterprise());
        }
        LOGGER.info("pushCheckResult recipeId:{}, result:{}", recipeId, JSONUtils.toString(result));
        return result;
    }

    /**
     * 查找供应商
     *
     * @param recipeIds 处方列表
     * @param ext       额外信息
     * @return 供应商信息
     */
    @RpcService
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, Map ext, DrugsEnterprise drugsEnterprise) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        RecipeParameterDao parameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        String drugsEnterpriseList = parameterDao.getByName("drugsEnterpriseList");
        if (StringUtils.isNotEmpty(drugsEnterpriseList) && drugsEnterprise != null && hasDep(drugsEnterprise.getCallSys(), drugsEnterpriseList)) {
            //通过前置机调用
            IRecipeEnterpriseService recipeEnterpriseService = AppContextHolder.getBean("his.iRecipeEnterpriseService",IRecipeEnterpriseService.class);
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            Recipe recipe = recipeDAO.getByRecipeId(recipeIds.get(0));
            ScanRequestBean scanRequestBean = getScanRequestBean(recipe, drugsEnterprise);
            scanRequestBean.setExt(ext);
            List<DepDetailBean> depDetailBeans =  recipeEnterpriseService.findSupportDep(scanRequestBean);
            result.setObject(ObjectCopyUtils.convert(depDetailBeans, com.ngari.recipe.drugsenterprise.model.DepDetailBean.class));
            return result;
        }
        if (CollectionUtils.isNotEmpty(recipeIds) && null != drugsEnterprise) {
            AccessDrugEnterpriseService drugEnterpriseService = this.getServiceByDep(drugsEnterprise);
            result = drugEnterpriseService.findSupportDep(recipeIds, ext, drugsEnterprise);
            LOGGER.info("findSupportDep recipeIds={}, DrugEnterpriseResult={}", JSONUtils.toString(recipeIds), JSONUtils.toString(result));
        } else {
            LOGGER.warn("findSupportDep param error. recipeIds={}, drugsEnterprise={}", JSONUtils.toString(recipeIds), JSONUtils.toString(drugsEnterprise));
        }

        return result;
    }

    /**
     * 药品库存同步
     *
     * @return
     */
    @RpcService
    public DrugEnterpriseResult syncEnterpriseDrug() {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);

        List<DrugsEnterprise> drugsEnterpriseList = drugsEnterpriseDAO.findAllDrugsEnterpriseByStatus(1);
        if (CollectionUtils.isNotEmpty(drugsEnterpriseList)) {
            AccessDrugEnterpriseService drugEnterpriseService;
            for (DrugsEnterprise drugsEnterprise : drugsEnterpriseList) {
                if (null != drugsEnterprise) {
                    List<Integer> drugIdList = saleDrugListDAO.findSynchroDrug(drugsEnterprise.getId());
                    if (CollectionUtils.isNotEmpty(drugIdList)) {
                        drugEnterpriseService = this.getServiceByDep(drugsEnterprise);
                        if (null != drugEnterpriseService) {
                            LOGGER.info("syncDrugTask 开始同步药企[{}]药品，药品数量[{}]", drugsEnterprise.getName(), drugIdList.size());
                            drugEnterpriseService.syncEnterpriseDrug(drugsEnterprise, drugIdList);
                        }
                    } else {
                        LOGGER.warn("syncDrugTask 药企[{}]无可同步药品.", drugsEnterprise.getName());
                    }
                }
            }
        }

        return result;
    }


    @RpcService
    public void updateAccessTokenById(Integer code, Integer depId) {
        AccessDrugEnterpriseService drugEnterpriseService = getBean(COMMON_SERVICE, AccessDrugEnterpriseService.class);
        drugEnterpriseService.updateAccessTokenById(code, depId);
    }

    public String updateAccessToken(List<Integer> drugsEnterpriseIds) {
        AccessDrugEnterpriseService drugEnterpriseService = getBean(COMMON_SERVICE, AccessDrugEnterpriseService.class);
        return drugEnterpriseService.updateAccessToken(drugsEnterpriseIds);
    }

    public void updateAccessTokenByDep(DrugsEnterprise drugsEnterprise) {
        AccessDrugEnterpriseService service = getServiceByDep(drugsEnterprise);
        service.tokenUpdateImpl(drugsEnterprise);
    }

    /**
     * 根据单个处方ID获取具体药企实现
     *
     * @param recipeId
     * @return
     */
    public DrugEnterpriseResult getServiceByRecipeId(Integer recipeId) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        if (null == recipeId) {
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setMsg("处方ID为空");
        }

        if (DrugEnterpriseResult.SUCCESS.equals(result.getCode())) {
            DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
            RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
            //PS:药企ID取的是订单表的药企ID
            Integer depId = recipeOrderDAO.getEnterpriseIdByRecipeId(recipeId);
            if (null != depId) {
                DrugsEnterprise dep = drugsEnterpriseDAO.get(depId);
                if (null != dep) {
                    result.setAccessDrugEnterpriseService(this.getServiceByDep(dep));
                    result.setDrugsEnterprise(dep);
                } else {
                    result.setCode(DrugEnterpriseResult.FAIL);
                    result.setMsg("药企" + depId + "未找到");
                }
            } else {
                result.setCode(DrugEnterpriseResult.FAIL);
                result.setMsg("处方单" + recipeId + "未分配药企");
            }
        }

        LOGGER.info("getServiceByRecipeId recipeId:{}, result:{}", recipeId, result.toString());
        return result;
    }

    /**
     * 通过药企实例获取具体实现
     *
     * @param drugsEnterprise
     * @return
     */
    public AccessDrugEnterpriseService getServiceByDep(DrugsEnterprise drugsEnterprise) {
        AccessDrugEnterpriseService drugEnterpriseService = null;
        if (null != drugsEnterprise) {
            //先获取指定实现标识，没有指定则根据帐号名称来获取
            String callSys = StringUtils.isEmpty(drugsEnterprise.getCallSys()) ? drugsEnterprise.getAccount() : drugsEnterprise.getCallSys();
            String beanName = COMMON_SERVICE;
            if (StringUtils.isNotEmpty(callSys)) {
                beanName = callSys + "RemoteService";
            }
            try {
                LOGGER.info("getServiceByDep 获取[{}]协议实现.service=[{}]", drugsEnterprise.getName(), beanName);
                drugEnterpriseService = getBean(beanName, AccessDrugEnterpriseService.class);
            } catch (Exception e) {
                LOGGER.warn("getServiceByDep 未找到[{}]药企实现，使用通用协议处理. beanName={}", drugsEnterprise.getName(), beanName,e);
                drugEnterpriseService = getBean(COMMON_SERVICE, AccessDrugEnterpriseService.class);
            }
        }

        return drugEnterpriseService;
    }

    /**
     * 获取药企帐号
     *
     * @param depId
     * @return
     */
    public String getDepAccount(Integer depId) {
        if (null == depId) {
            return null;
        }
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        return drugsEnterpriseDAO.getAccountById(depId);
    }

    /**
     * 获取钥世圈订单详情URL
     *
     * @param recipe
     * @return
     */
    public String getYsqOrderInfoUrl(Recipe recipe) {
        RecipeCacheService cacheService = ApplicationUtils.getRecipeService(RecipeCacheService.class);
        String backUrl = "";
        String ysqUrl = cacheService.getParam(ParameterConstant.KEY_YSQ_SKIP_URL);
        if (RecipeStatusConstant.FINISH != recipe.getStatus()) {
            backUrl = ysqUrl + "Order/Index?id=0&inbillno=" + recipe.getClinicOrgan() + YsqRemoteService.YSQ_SPLIT + recipe.getRecipeCode();
        }
        return backUrl;
    }

    private boolean hasDep(String organ, String parames){
        if (StringUtils.isNotEmpty(parames)) {
            String[] organs = parames.split(",");
            for (String o : organs) {
                if (organ.equals(o)) {
                    return true;
                }
            }
        }
        return false;
    }
}
