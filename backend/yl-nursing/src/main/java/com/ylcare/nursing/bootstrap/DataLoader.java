package com.ylcare.nursing.bootstrap;

import com.ylcare.nursing.domain.Elderly;
import com.ylcare.nursing.domain.Staff;
import com.ylcare.nursing.repo.ElderlyRepo;
import com.ylcare.nursing.repo.StaffRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {
    private final ElderlyRepo elderlyRepo;
    private final StaffRepo staffRepo;

    public DataLoader(ElderlyRepo elderlyRepo, StaffRepo staffRepo) {
        this.elderlyRepo = elderlyRepo;
        this.staffRepo = staffRepo;
    }

    @Override
    public void run(String... args) {
        if (staffRepo.count() > 0) {
            return;
        }
        staffRepo.save(staff("李护理", "caregiver"));
        staffRepo.save(staff("张院长", "admin"));
        elderlyRepo.save(elderly("3", "王奶奶", "女"));
        elderlyRepo.save(elderly("5", "李爷爷", "男"));
        elderlyRepo.save(elderly("2", "张奶奶", "女"));
    }

    private Staff staff(String name, String role) {
        Staff s = new Staff();
        s.setName(name);
        s.setRole(role);
        return s;
    }

    private Elderly elderly(String bed, String name, String gender) {
        Elderly e = new Elderly();
        e.setBedNo(bed);
        e.setName(name);
        e.setAlias(name);
        e.setGender(gender);
        e.setStatus("in");
        return e;
    }
}
