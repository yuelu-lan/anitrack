package com.anitrack.application.converter;

import com.anitrack.application.model.UserBO;
import com.anitrack.domain.user.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserBOConverter {

    UserBO user2BO(User user);
}
