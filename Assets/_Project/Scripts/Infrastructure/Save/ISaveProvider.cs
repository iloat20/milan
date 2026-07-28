namespace Milan.Infrastructure.Save
{
    public interface ISaveProvider { void Save(string json); string Load(); void Delete(); }
}
