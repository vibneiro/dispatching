package vibneiro.idgenerators.time;

public class SystemDateSource implements DateSource {
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
