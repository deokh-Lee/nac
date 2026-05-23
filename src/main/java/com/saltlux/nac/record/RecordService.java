package com.saltlux.nac.record;

import com.saltlux.nac.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecordService {

    private final RecordMapper recordMapper;

    public RecordService(RecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    public List<RecordResponse> findAll() {
        return recordMapper.findAll()
                .stream()
                .map(RecordResponse::from)
                .toList();
    }

    public RecordResponse findById(Long id) {
        return RecordResponse.from(getRecord(id));
    }

    @Transactional
    public RecordResponse create(RecordRequest request) {
        Record record = new Record(request.title(), request.description());
        recordMapper.insert(record);
        return findById(record.getId());
    }

    @Transactional
    public RecordResponse update(Long id, RecordRequest request) {
        getRecord(id);

        Record record = new Record(request.title(), request.description());
        record.setId(id);
        recordMapper.update(record);

        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        getRecord(id);
        recordMapper.deleteById(id);
    }

    private Record getRecord(Long id) {
        return recordMapper.findById(id)
                .orElseThrow(() -> new NotFoundException("Record not found. id=" + id));
    }
}
