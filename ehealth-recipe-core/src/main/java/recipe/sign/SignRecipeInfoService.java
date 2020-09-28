package recipe.sign;

import com.alibaba.fastjson.JSONObject;
import com.ngari.base.department.service.IDepartmentService;
import com.ngari.patient.dto.DoctorExtendDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.DoctorExtendService;
import com.ngari.patient.service.PatientService;
import com.ngari.recipe.ca.CaSignResultUpgradeBean;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.entity.sign.SignDoctorRecipeInfo;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.sign.ISignRecipeInfoService;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.ApplicationUtils;
import recipe.ca.vo.CaSignResultVo;
import recipe.constant.ErrorCode;
import recipe.dao.RecipeExtendDAO;
import recipe.dao.sign.SignDoctorRecipeInfoDAO;
import recipe.service.RecipeService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RpcBean
public class SignRecipeInfoService implements ISignRecipeInfoService {

    private static final Logger logger = LoggerFactory.getLogger(SignDoctorRecipeInfo.class);

    @Autowired
    private DoctorExtendService doctorExtendService;

    @Autowired
    private SignDoctorRecipeInfoDAO signDoctorRecipeInfoDAO;

    @RpcService
    public boolean updateSignInfoByRecipeId(SignDoctorRecipeInfo signDoctorRecipeInfo){
        logger.info("SignRecipeInfoService updateSignInfoByRecipeId info[{}]", JSONObject.toJSONString(signDoctorRecipeInfo));

        if (signDoctorRecipeInfo == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "signDoctorRecipeInfo is not null!");
        }
        SignDoctorRecipeInfo recipeInfo = signDoctorRecipeInfoDAO.getRecipeInfoByRecipeId(signDoctorRecipeInfo.getRecipeId());
        if (signDoctorRecipeInfo == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "处方订单不存在");
        }

        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getSignCaDateDoc())) {
            recipeInfo.setSignCaDateDoc(signDoctorRecipeInfo.getSignCaDateDoc());
        }
        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getSignCodeDoc())) {
            recipeInfo.setSignCodeDoc(signDoctorRecipeInfo.getSignCodeDoc());
        }
        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getSignFileDoc())) {
            recipeInfo.setSignFileDoc(signDoctorRecipeInfo.getSignFileDoc());
        }
        if (signDoctorRecipeInfo.getSignDate() != null) {
            recipeInfo.setSignDate(signDoctorRecipeInfo.getSignDate());
        }
        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getSignCaDatePha())) {
            recipeInfo.setSignCaDatePha(signDoctorRecipeInfo.getSignCaDatePha());
        }
        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getSignCodePha())) {
            recipeInfo.setSignCodePha(signDoctorRecipeInfo.getSignCodePha());
        }
        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getSignFilePha())) {
            recipeInfo.setSignFilePha(signDoctorRecipeInfo.getSignFilePha());
        }
        if (signDoctorRecipeInfo.getCheckDatePha() != null) {
            recipeInfo.setCheckDatePha(signDoctorRecipeInfo.getCheckDatePha());
        }
        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getSignRemarkDoc())) {
            recipeInfo.setSignRemarkDoc(signDoctorRecipeInfo.getSignRemarkDoc());
        }
        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getSignRemarkPha())) {
            recipeInfo.setSignRemarkPha(signDoctorRecipeInfo.getSignRemarkPha());
        }
        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getSignBefText())) {
            recipeInfo.setSignBefText(signDoctorRecipeInfo.getSignBefText());
        }
        if (StringUtils.isNotEmpty(signDoctorRecipeInfo.getType())) {
            recipeInfo.setType(signDoctorRecipeInfo.getType());
        }
        signDoctorRecipeInfoDAO.update(recipeInfo);
        return true;
    }

    @RpcService
    public void update(SignDoctorRecipeInfo signDoctorRecipeInfo){
        signDoctorRecipeInfoDAO.update(signDoctorRecipeInfo);
    }

    @RpcService
    public SignDoctorRecipeInfo get(Integer recipeId) {
        return signDoctorRecipeInfoDAO.getRecipeInfoByRecipeId(recipeId);
    }

    @Deprecated
    @RpcService
    public SignDoctorRecipeInfo getSignInfoByRecipeId(Integer serverId) {
        return getSignInfoByServerIdAndServerType(serverId, 1);
    }

    @RpcService
    public SignDoctorRecipeInfo getSignInfoByServerIdAndServerType(Integer serverId, Integer serverType){
        logger.info("getSignInfoByRecipeId start serverId={}= serverType={}=" , serverId, serverType);
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
        RecipeBean recipeBean = recipeService.getByRecipeId(serverId);
        if (recipeBean == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "处方订单不存在");
        }

        SignDoctorRecipeInfo signDoctorRecipeInfo = signDoctorRecipeInfoDAO.getRecipeInfoByRecipeIdAndServerType(serverId, serverType);
        if (signDoctorRecipeInfo == null) {
            signDoctorRecipeInfo = new SignDoctorRecipeInfo();
            signDoctorRecipeInfo.setRecipeId(serverId);
            signDoctorRecipeInfo.setCreateDate(new Date());
            signDoctorRecipeInfo.setLastmodify(new Date());
            signDoctorRecipeInfo.setServerType(serverType);
            signDoctorRecipeInfo = signDoctorRecipeInfoDAO.save(signDoctorRecipeInfo);
            return signDoctorRecipeInfo;
        }

        if(recipeBean.getDoctor() != null) {
            DoctorExtendDTO doctorExtendDTODoc =  doctorExtendService.getByDoctorId(recipeBean.getDoctor());
            if (doctorExtendDTODoc != null) {
                signDoctorRecipeInfo.setSealDataDoc(doctorExtendDTODoc.getSealData());
            }
        }

        if(recipeBean.getChecker() != null){
            DoctorExtendDTO doctorExtendDTOPha = doctorExtendService.getByDoctorId(recipeBean.getChecker());
            if (doctorExtendDTOPha != null) {
                signDoctorRecipeInfo.setSealDataPha(doctorExtendDTOPha.getSealData());
            }
        }
        return signDoctorRecipeInfo;
    }

    @RpcService
    public SignDoctorRecipeInfo setSignRecipeInfoByServerIdAndServerType(Integer serverId, boolean isDoctor, String serCode, Integer serverType){
        SignDoctorRecipeInfo signDoctorRecipeInfo = new SignDoctorRecipeInfo();
        if (isDoctor) {
            signDoctorRecipeInfo.setCaSerCodeDoc(serCode);
        } else {
            signDoctorRecipeInfo.setCaSerCodePha(serCode);
        }
        signDoctorRecipeInfo.setCreateDate(new Date());
        signDoctorRecipeInfo.setLastmodify(new Date());
        signDoctorRecipeInfo.setRecipeId(serverId);
        signDoctorRecipeInfo.setServerType(serverType);
        return signDoctorRecipeInfoDAO.save(signDoctorRecipeInfo);
    }

    @Deprecated
    @RpcService
    public SignDoctorRecipeInfo setSignRecipeInfo(Integer recipeId, boolean isDoctor, String serCode){
        return setSignRecipeInfoByServerIdAndServerType(recipeId, isDoctor, serCode, 1);
    }

    @RpcService
    @Override
    public void setMedicalSignInfoByRecipeId(Integer recipeId){
        SignDoctorRecipeInfo signDoctorRecipeInfo = signDoctorRecipeInfoDAO.getRecipeInfoByRecipeId(recipeId);
        SignDoctorRecipeInfo s = signDoctorRecipeInfoDAO.getInfoByRecipeIdAndServiceType(recipeId, 2);
        if (signDoctorRecipeInfo != null && s == null) {
            SignDoctorRecipeInfo signInfo = new SignDoctorRecipeInfo();
            signInfo.setRecipeId(recipeId);
            signInfo.setServerType(2);
            signInfo.setSignCodeDoc(signDoctorRecipeInfo.getSignCodeDoc());
            signInfo.setSignCaDateDoc(signDoctorRecipeInfo.getSignCaDateDoc());
            signInfo.setSignCodePha(signDoctorRecipeInfo.getSignCodePha());
            signInfo.setSignCaDatePha(signDoctorRecipeInfo.getSignCaDatePha());

            RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
            RecipeBean recipeBean = recipeService.getByRecipeId(recipeId);

            RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
            RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipeId);

            PatientService patientService = BasicAPI.getService(PatientService.class);
            PatientDTO p = patientService.getPatientByMpiId(recipeBean.getMpiid());

            IDepartmentService iDepartmentService = ApplicationUtils.getBaseService(IDepartmentService.class);
            String departName = iDepartmentService.getNameById(recipeBean.getDepart());

            JSONObject json = new JSONObject();
            json.put("patientName",p.getPatientName());
            json.put("patientMobile",p.getMobile());
            json.put("patientIdcard",p.getCertificate());
            json.put("doctorName",recipeBean.getDoctorName());
            json.put("dateTime",recipeBean.getCreateDate());
            json.put("DepartName",departName);
            json.put("organDiseaseName",recipeBean.getOrganDiseaseName());
            json.put("mainDieaseDescribe",recipeExtend.getMainDieaseDescribe());
            signInfo.setSignBefText(json.toJSONString());
            signDoctorRecipeInfoDAO.save(signInfo);
        }
    }

    @RpcService
    public Map getSignInfoByRegisterID(Integer recipeId, String type){
        logger.info("getSignInfoByRegisterID start recipeId={}=,type={}=", recipeId, type);
        SignDoctorRecipeInfo signDoctorRecipeInfo = signDoctorRecipeInfoDAO.getRecipeInfoByRecipeIdAndType(recipeId, type);
        if (null == signDoctorRecipeInfo) {
            signDoctorRecipeInfo = signDoctorRecipeInfoDAO.getRecipeInfoByRecipeId(recipeId);
        }
        Map map = new HashMap(4);
        if (null != signDoctorRecipeInfo) {
            map.put("signCodeDoc", signDoctorRecipeInfo.getSignCodeDoc());
            map.put("signRemarkDoc", signDoctorRecipeInfo.getSignRemarkDoc());
            map.put("signCodePha", signDoctorRecipeInfo.getSignCodePha());
            map.put("signRemarkPha", signDoctorRecipeInfo.getSignRemarkPha());
        }
        return map;
    }

    @RpcService
    public void saveSignInfo(Integer recipeId, boolean isDoctor, CaSignResultVo signResult, String type) {
        logger.info("saveSignInfoByRecipe infos recipeId={}=,isDoctor={}=, signResult={}=,type={}=",recipeId, isDoctor, JSONObject.toJSONString(signResult),type);
        SignDoctorRecipeInfo signDoctorRecipeInfo = signDoctorRecipeInfoDAO.getRecipeInfoByRecipeIdAndType(recipeId, type);
        if (signDoctorRecipeInfo == null) {
            signDoctorRecipeInfo = new SignDoctorRecipeInfo();
            signDoctorRecipeInfo.setRecipeId(recipeId);
            signDoctorRecipeInfo.setCreateDate(new Date());
            signDoctorRecipeInfo = getInfoByResultVo(signDoctorRecipeInfo, signResult,isDoctor, type);
            signDoctorRecipeInfo.setServerType(1);
            signDoctorRecipeInfoDAO.save(signDoctorRecipeInfo);
        } else {
            signDoctorRecipeInfo = getInfoByResultVo(signDoctorRecipeInfo, signResult,isDoctor, type);
            signDoctorRecipeInfoDAO.update(signDoctorRecipeInfo);
        }
    }

    /** 重庆ca专用
     * ca保存ca信息
     * @param recipeId 处方ID
     * @param signCode 签名摘要
     * @param signCrt 签名值
     * @param isDoctor true 医生 false 药师
     */
    @RpcService
    public void saveSignInfoByRecipe(Integer recipeId, String signCode, String signCrt, boolean isDoctor, String type){

        logger.info("saveSignInfoByRecipe infos recipeId={}=,signCode={}=,signCrt={}=,isDoctor={}=, type={}=",recipeId, signCode, signCrt, isDoctor, type);
        SignDoctorRecipeInfo signDoctorRecipeInfo = signDoctorRecipeInfoDAO.getRecipeInfoByRecipeIdAndType(recipeId, type);
        if (signDoctorRecipeInfo == null) {
            signDoctorRecipeInfo = new SignDoctorRecipeInfo();
            signDoctorRecipeInfo.setRecipeId(recipeId);
            signDoctorRecipeInfo.setCreateDate(new Date());
            signDoctorRecipeInfo = getInfo(signDoctorRecipeInfo, signCode, signCrt, isDoctor, type);
            signDoctorRecipeInfo.setServerType(1);
            signDoctorRecipeInfoDAO.save(signDoctorRecipeInfo);
        } else {
            signDoctorRecipeInfo = getInfo(signDoctorRecipeInfo, signCode, signCrt, isDoctor, type);
            signDoctorRecipeInfoDAO.update(signDoctorRecipeInfo);
        }
    }


    /**
     * 处方订单号修改签名数据
     *
     * @param signDoctorRecipeInfo
     * @return
     */
    @RpcService
    public Boolean updateSignInfoByRecipeInfo(SignDoctorRecipeInfo signDoctorRecipeInfo) {
        logger.info("updateSignInfoByRecipeInfo signDoctorRecipeInfo={}", JSONUtils.toString(signDoctorRecipeInfo));
        if (null == signDoctorRecipeInfo) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "入参为空");
        }

        if (null == signDoctorRecipeInfo.getRecipeId()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "处方号为空");
        }

        getSignInfoByRecipeId(signDoctorRecipeInfo.getRecipeId());

        return updateSignInfoByRecipeId(signDoctorRecipeInfo);
    }

    private SignDoctorRecipeInfo getInfoByResultVo(SignDoctorRecipeInfo signDoctorRecipeInfo, CaSignResultVo signResult, boolean isDoctor, String type) {
        String fileId = null;
        if(StringUtils.isNotEmpty(signResult.getSignPicture())){
            fileId = uploadPicture(signResult.getSignPicture());
        }
        if (isDoctor) {
            signDoctorRecipeInfo.setSignFileDoc(signResult.getFileId());
            signDoctorRecipeInfo.setSignCaDateDoc(signResult.getSignCADate());
            signDoctorRecipeInfo.setSignCodeDoc(signResult.getSignRecipeCode());
            signDoctorRecipeInfo.setSignPictureDoc(fileId);
        } else {
            signDoctorRecipeInfo.setSignFilePha(signResult.getFileId());
            signDoctorRecipeInfo.setSignCaDatePha(signResult.getSignCADate());
            signDoctorRecipeInfo.setSignCodePha(signResult.getSignRecipeCode());
            signDoctorRecipeInfo.setSignPicturePha(fileId);
        }
        signDoctorRecipeInfo.setLastmodify(new Date());
        signDoctorRecipeInfo.setType(type);
        return signDoctorRecipeInfo;
    }


    private SignDoctorRecipeInfo getInfo (SignDoctorRecipeInfo signDoctorRecipeInfo, String signCode, String signCrt, boolean isDoctor,String type) {
        if (isDoctor) {
            signDoctorRecipeInfo.setSignRemarkDoc(signCrt);
            signDoctorRecipeInfo.setSignCodeDoc(signCode);
        } else {
            signDoctorRecipeInfo.setSignRemarkPha(signCrt);
            signDoctorRecipeInfo.setSignCodePha(signCode);
        }
        signDoctorRecipeInfo.setLastmodify(new Date());
        signDoctorRecipeInfo.setType(type);
        return signDoctorRecipeInfo;
    }

    @RpcService
    public String uploadPicture(String picture) {
        byte[] data = Base64.decodeBase64(picture.getBytes());
        if (data == null) {
            return null;
        }
        OutputStream fileOutputStream = null;
        try {
            //先生成本地文件
            String prefix = picture.substring(0,4);
            String fileName = "caPicture_"+prefix+".jpeg";
            File file = new File(fileName);
            fileOutputStream = new FileOutputStream(file);
            if (data.length > 0) {
                fileOutputStream.write(data, 0, data.length);
                fileOutputStream.flush();
            }

            FileMetaRecord meta = new FileMetaRecord();
            meta.setManageUnit("eh");
           // meta.setOwner(token.getUserId());
            meta.setLastModify(new Date());
            meta.setUploadTime(new Date());
            //0需要验证 31不需要
            meta.setMode(0);
            meta.setCatalog("other-doc");
            meta.setContentType("image/jpeg");
            meta.setFileName(fileName);
            meta.setFileSize(file.length());
            logger.info("uploadPicture.meta=[{}]",JSONUtils.toString(meta));
            FileService.instance().upload(meta, file);
            file.delete();
            return meta.getFileId();
        } catch (Exception e) {
            logger.error("uploadPicture uploadRecipeFile exception:" + e.getMessage());
        } finally {
            try {
                fileOutputStream.close();
            } catch (Exception e) {
                logger.error("uploadPicture uploadRecipeFile exception:" + e.getMessage());
            }

        }
        return null;
    }

    @Override
    public void saveCaSignResult(CaSignResultUpgradeBean caSignResult) {
        logger.info("SignRecipeInfoService.saveCaSignResult caSignResultBean=[{}]",JSONUtils.toString(caSignResult));
        if (caSignResult != null){
            SignDoctorRecipeInfo signDoctorRecipeInfo = new SignDoctorRecipeInfo();
            // 业务id
            signDoctorRecipeInfo.setRecipeId(caSignResult.getBussId());
            // 业务类型
            signDoctorRecipeInfo.setServerType(caSignResult.getBusstype());
            // 电子签名
            signDoctorRecipeInfo.setSignCodeDoc(caSignResult.getSignCode());
            // 电子签章
            // 时间戳
            signDoctorRecipeInfo.setSignCaDateDoc(caSignResult.getSignDate());
            // ca类型
            signDoctorRecipeInfo.setType(caSignResult.getCaType());
            // 签名原文
            signDoctorRecipeInfo.setSignBefText(caSignResult.getSignText());
            // 手签图片
            String pictureId = uploadPicture(caSignResult.getSignPicture());
            signDoctorRecipeInfo.setSignPictureDoc(pictureId);
            // 创建时间
            signDoctorRecipeInfo.setCreateDate(new Date());
            // 修改时间
            signDoctorRecipeInfo.setLastmodify(new Date());

           signDoctorRecipeInfoDAO.save(signDoctorRecipeInfo);
        }
    }
}
