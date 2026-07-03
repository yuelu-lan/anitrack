package com.anitrack.infra.converter;

import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.domain.user.model.User;
import com.anitrack.infra.dal.po.UserPO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserConverter {

    UserPO toPO(User user);

    default User toDomain(UserPO po) {
        if (po == null) {
            return null;
        }
        return User.reconstitute(po.getId(), po.getUsername(), po.getPasswordHash(),
            po.getNickname(), po.getAvatarUrl(), map(po.getRole()));
    }

    default String map(UserRole role) {
        return role == null ? null : role.name();
    }

    default UserRole map(String role) {
        return role == null ? null : UserRole.valueOf(role);
    }
}
