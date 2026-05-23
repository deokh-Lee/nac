package com.saltlux.nac.record;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecordMapper {

    List<Record> findAll();

    Optional<Record> findById(Long id);

    int insert(Record record);

    int update(Record record);

    int deleteById(Long id);
}
