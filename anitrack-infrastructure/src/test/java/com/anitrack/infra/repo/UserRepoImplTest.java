package com.anitrack.infra.repo;

import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.domain.user.model.User;
import com.anitrack.infra.converter.UserConverter;
import com.anitrack.infra.dal.mapper.UserMapper;
import com.anitrack.infra.dal.po.UserPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepoImplTest {

    @Mock
    private UserMapper mockUserMapper;

    @Mock
    private UserConverter mockUserConverter;

    @InjectMocks
    private UserRepoImpl sut;

    @Test
    void listByIds_whenIdsProvided_shouldReturnConvertedList() {
        // given
        UserPO po = new UserPO();
        User user = User.reconstitute(1L, "bob", "hash", "Bob", null, UserRole.USER);
        when(mockUserMapper.selectByIds(List.of(1L))).thenReturn(List.of(po));
        when(mockUserConverter.toDomain(po)).thenReturn(user);

        // when
        List<User> result = sut.listByIds(List.of(1L));

        // then
        assertThat(result).containsExactly(user);
    }

    @Test
    void listByIds_whenIdsEmpty_shouldReturnEmptyListWithoutQuerying() {
        // when
        List<User> result = sut.listByIds(List.of());

        // then
        assertThat(result).isEmpty();
        verify(mockUserMapper, never()).selectByIds(any());
    }
}
