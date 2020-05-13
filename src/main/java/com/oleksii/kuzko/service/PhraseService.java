package com.oleksii.kuzko.service;

import com.oleksii.kuzko.dao.PhraseDao;
import com.oleksii.kuzko.model.Phrase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author The Weather Company, An IBM Business
 */
@Service
public class PhraseService {

    private final PhraseDao phraseDao;

    public PhraseService(PhraseDao phraseDao) {
        this.phraseDao = phraseDao;
    }

    public Phrase getRandom() {
        return null;
    }

    public List<Phrase> getAll() {
        return phraseDao.getAll();
    }

    public List<Phrase> getAllMysql() {
        return phraseDao.getAllMysql();
    }

    @Transactional
    public boolean copy() {
        List<Phrase> allPhrases = getAllMysql();
        for(Phrase currentPhrase : allPhrases){
            phraseDao.createPhrase(currentPhrase);
        }
        return true;
    }
}
