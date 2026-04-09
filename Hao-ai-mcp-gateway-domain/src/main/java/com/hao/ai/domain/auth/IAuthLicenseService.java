package com.hao.ai.domain.auth;

import com.hao.ai.domain.auth.model.entity.LicenseCommandEntity;
import com.hao.ai.domain.auth.model.valobj.SessionVO;

public interface IAuthLicenseService {
    boolean checkLicense(LicenseCommandEntity licenseCommandEntity);
}
