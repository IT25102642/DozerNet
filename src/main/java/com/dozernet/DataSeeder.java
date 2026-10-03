package com.dozernet;

import com.dozernet.common.model.Role;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module3_fleet.entity.Ownership;
import com.dozernet.module3_fleet.repository.MachineRepository;
import com.dozernet.module4_operator.entity.OperatorProfile;
import com.dozernet.module4_operator.repository.OperatorProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Seeds demo accounts (with correctly BCrypt-hashed passwords) so the app is
 * demonstrable immediately. Runs first (Order 1); module-specific seed data
 * runs afterwards. Idempotent: only seeds when the users table is empty.
 *
 * Demo password for every seeded account: "Password123" (meets the uppercase + digit policy).
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    public static final String DEMO_PASSWORD = "Password123";

    private static final Map<MachineType, String> TYPE_IMAGES = new EnumMap<>(MachineType.class);

    static {
        TYPE_IMAGES.put(MachineType.BACKHOE_LOADER, "/images/machines/backhoe-loader.jpg");
        TYPE_IMAGES.put(MachineType.EXCAVATOR, "/images/machines/excavator.jpg");
        TYPE_IMAGES.put(MachineType.WHEEL_LOADER, "/images/machines/wheel-loader.jpg");
        TYPE_IMAGES.put(MachineType.SKID_STEER, "/images/machines/skid-steer.jpg");
        TYPE_IMAGES.put(MachineType.TELEHANDLER, "/images/machines/telehandler.jpg");
        TYPE_IMAGES.put(MachineType.COMPACTOR, "/images/machines/compactor.jpg");
        TYPE_IMAGES.put(MachineType.BULLDOZER, "/images/machines/bulldozer.jpg");
        // Interim photos for the two newest categories - replace with
        // motor-grader.jpg / dump-truck.jpg once real shots are available.
        TYPE_IMAGES.put(MachineType.MOTOR_GRADER, "/images/machines/bulldozer.jpg");
        TYPE_IMAGES.put(MachineType.DUMP_TRUCK, "/images/machines/wheel-loader.jpg");
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MachineRepository machineRepository;
    private final OperatorProfileRepository operatorProfileRepository;

    public DataSeeder(UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      MachineRepository machineRepository,
                      OperatorProfileRepository operatorProfileRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.machineRepository = machineRepository;
        this.operatorProfileRepository = operatorProfileRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("Seeding demo users (password for all: {})", DEMO_PASSWORD);

            seed("Admin User", "admin@dozernet.lk", "0770000001", "199001010001", Role.ADMIN, true);
            seed("Chamara Perera", "customer@dozernet.lk", "0771111111", "199101010002", Role.CUSTOMER, true);
            seed("Nimal Fernando", "owner@dozernet.lk", "0772222222", "199201010003", Role.OWNER, true);
            User operator = seed("Sunil Bandara", "operator@dozernet.lk", "0773333333", "199301010004", Role.OPERATOR, true);

            seedOperatorProfile(operator);
        }

        // Fleet is idempotent by registration number — safe on every boot.
        seedMachines();

        // Always fill missing machine photos (existing DB rows included).
        ensureMachineImages();
        ensureIdentityCardNumbers();
    }

    private void ensureIdentityCardNumbers() {
        int updated = 0;
        int seq = 1;
        for (User u : userRepository.findAll()) {
            String current = u.getIdentityCardNumber();
            // Blank, or old seeder tokens (19990000xxxx) that look like real NICs.
            boolean needsPlaceholder = current == null || current.isBlank()
                    || current.matches("19990000\\d{4}");
            if (!needsPlaceholder) {
                continue;
            }
            // LEGACY###### fits the 12-char column but fails ValidationPatterns.NIC,
            // so it cannot block a genuine registration.
            String nic;
            do {
                nic = String.format("LEGACY%06d", seq++);
            } while (userRepository.existsByIdentityCardNumber(nic));
            u.setIdentityCardNumber(nic);
            userRepository.save(u);
            updated++;
        }
        if (updated > 0) {
            log.info("Assigned placeholder NIC to {} existing user(s)", updated);
        }
    }

    private void ensureMachineImages() {
        int updated = 0;
        for (Machine m : machineRepository.findAll()) {
            if (m.getImageUrl() == null || m.getImageUrl().isBlank()) {
                m.setImageUrl(imageFor(m.getType()));
                machineRepository.save(m);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Assigned sample images to {} machine(s)", updated);
        }
    }

    private static String imageFor(MachineType type) {
        return TYPE_IMAGES.getOrDefault(type, "/images/machines/excavator.jpg");
    }

    private User seed(String name, String email, String phone, String nic, Role role, boolean verified) {
        User u = new User(name, email, phone, nic, passwordEncoder.encode(DEMO_PASSWORD), role);
        u.setVerified(verified);
        return userRepository.save(u);
    }

    private void seedOperatorProfile(User operator) {
        OperatorProfile p = new OperatorProfile();
        p.setUser(operator);
        p.setLicenceNumber("B1234567");
        p.setExperienceYears(6);
        p.setIndependent(false);
        p.setVerified(true);
        operatorProfileRepository.save(p);
    }

    private void seedMachines() {
        // Digging
        company("JCB 3CX Backhoe Loader", MachineType.BACKHOE_LOADER, "WP CAB-3421", "Colombo", "18500.00",
                "Versatile backhoe loader ideal for digging, loading and site work.");
        company("JCB 4CX Backhoe Loader", MachineType.BACKHOE_LOADER, "WP CAB-5510", "Negombo", "21000.00",
                "Heavy-duty backhoe for deeper trenches and utility work.");
        company("JCB JS205 Excavator", MachineType.EXCAVATOR, "WP EXC-8890", "Gampaha", "32000.00",
                "20-tonne tracked excavator for heavy earthmoving.");
        company("JCB 86C-1 Midi Excavator", MachineType.EXCAVATOR, "WP EXC-2201", "Colombo", "24500.00",
                "Compact excavator for urban digging and foundations.");
        company("JCB JS130 Excavator", MachineType.EXCAVATOR, "CP EXC-4412", "Kandy", "28500.00",
                "Mid-size excavator for site excavation and drainage.");

        // Earthmoving
        company("JCB 215X Bulldozer", MachineType.BULLDOZER, "NW BLZ-1001", "Kurunegala", "35000.00",
                "Tracked dozer for site clearing and bulk earthmoving.");
        company("JCB 300X Bulldozer", MachineType.BULLDOZER, "SP BLZ-2208", "Matara", "42000.00",
                "High-power dozer for large cut-and-fill projects.");
        company("JCB 205T Bulldozer", MachineType.BULLDOZER, "CP BLZ-3315", "Nuwara Eliya", "31000.00",
                "Compact dozer for hillside grading and access roads.");

        // Loading
        company("JCB 433 Wheel Loader", MachineType.WHEEL_LOADER, "CP WLD-1200", "Kandy", "27500.00",
                "High-capacity wheel loader for aggregate and material handling.");
        company("JCB 437 Wheel Loader", MachineType.WHEEL_LOADER, "WP WLD-8802", "Gampaha", "29500.00",
                "Wheel loader for quarry and stockyard loading.");
        company("JCB 411 Wheel Loader", MachineType.WHEEL_LOADER, "SP WLD-5504", "Galle", "23000.00",
                "Agile loader for sand, gravel and site materials.");
        company("JCB Robot 155 Skid Steer", MachineType.SKID_STEER, "WP SKD-4502", "Colombo", "14000.00",
                "Compact skid steer loader for tight urban sites.");
        company("JCB 270 Skid Steer", MachineType.SKID_STEER, "WP SKD-6711", "Kalutara", "15500.00",
                "Skid steer with attachments for clean-up and loading.");

        // Lifting
        company("JCB 540-170 Telehandler", MachineType.TELEHANDLER, "SP TEL-7781", "Galle", "24000.00",
                "17-metre telehandler for lifting and placing loads at height.");
        company("JCB 535-95 Telehandler", MachineType.TELEHANDLER, "WP TEL-3340", "Colombo", "22000.00",
                "Telehandler for pallet and materials placement on mid-rise sites.");
        company("JCB 560-80 Telehandler", MachineType.TELEHANDLER, "CP TEL-9912", "Kandy", "26500.00",
                "High-capacity telehandler for heavy lifts and stacking.");

        // Compacting
        company("JCB VMT260 Compactor", MachineType.COMPACTOR, "NW CMP-3390", "Kurunegala", "16500.00",
                "Vibratory roller for road and foundation compaction.");
        company("JCB Vibromax VM166", MachineType.COMPACTOR, "WP CMP-4488", "Colombo", "18000.00",
                "Soil and asphalt roller for roadwork finishing.");
        company("JCB CT160 Compactor", MachineType.COMPACTOR, "SP CMP-1120", "Hambantota", "15000.00",
                "Tandem roller for pathways and small road sections.");

        // Grading
        company("JCB 1110 Motor Grader", MachineType.MOTOR_GRADER, "NW GRD-2240", "Kurunegala", "30000.00",
                "Motor grader for road levelling, cambering and gravel finishing.");
        company("JCB 1155 Motor Grader", MachineType.MOTOR_GRADER, "CP GRD-7705", "Kandy", "33500.00",
                "Heavy grader for highway sub-base preparation.");

        // Hauling
        company("Tata Prima 2528 Tipper", MachineType.DUMP_TRUCK, "WP DMP-6620", "Colombo", "19500.00",
                "10-cube tipper for soil, aggregate and debris haulage.");
        company("Ashok Leyland 2518 Tipper", MachineType.DUMP_TRUCK, "SP DMP-8834", "Galle", "18500.00",
                "Site tipper for sand and metal deliveries.");
    }

    private void company(String model, MachineType type, String reg, String location,
                         String dailyRate, String description) {
        if (machineRepository.existsByRegistrationNumber(reg)) {
            return;
        }
        Machine m = new Machine();
        m.setModel(model);
        m.setType(type);
        m.setRegistrationNumber(reg);
        m.setLocation(location);
        m.setDailyRate(new BigDecimal(dailyRate));
        m.setDescription(description);
        m.setImageUrl(imageFor(type));
        m.setOwnership(Ownership.COMPANY);
        m.setVerified(true);
        machineRepository.save(m);
    }
}
