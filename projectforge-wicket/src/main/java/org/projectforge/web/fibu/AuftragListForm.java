/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2014 Kai Reinhard (k.reinhard@micromata.de)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.web.fibu;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.projectforge.business.fibu.AuftragDao;
import org.projectforge.business.fibu.AuftragFilter;
import org.projectforge.business.fibu.AuftragsPositionsArt;
import org.projectforge.business.fibu.AuftragsPositionsPaymentType;
import org.projectforge.business.fibu.AuftragsStatistik;
import org.projectforge.business.utils.CurrencyFormatter;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.web.user.UserSelectPanel;
import org.projectforge.web.wicket.AbstractListForm;
import org.projectforge.web.wicket.WebConstants;
import org.projectforge.web.wicket.components.LabelValueChoiceRenderer;
import org.projectforge.web.wicket.components.YearListCoiceRenderer;
import org.projectforge.web.wicket.flowlayout.DivPanel;
import org.projectforge.web.wicket.flowlayout.DivTextPanel;
import org.projectforge.web.wicket.flowlayout.FieldsetPanel;
import org.projectforge.web.wicket.flowlayout.Select2MultiChoicePanel;
import org.projectforge.web.wicket.flowlayout.TextStyle;

import com.vaynberg.wicket.select2.ChoiceProvider;
import com.vaynberg.wicket.select2.Response;
import com.vaynberg.wicket.select2.Select2MultiChoice;
import com.vaynberg.wicket.select2.TextChoiceProvider;

public class AuftragListForm extends AbstractListForm<AuftragFilter, AuftragListPage>
{
  private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(AuftragListForm.class);

  private static final long serialVersionUID = -5969136444233092172L;

  private AuftragsStatistik auftragsStatistik;

  @SpringBean
  private AuftragDao auftragDao;

  @SuppressWarnings("serial")
  @Override
  protected void init()
  {
    super.init();
    {
      // Statistics
      gridBuilder.newGridPanel();
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("statistics")).suppressLabelForWarning();
      fs.add(new DivTextPanel(fs.newChildId(), new Model<String>()
      {
        @Override
        public String getObject()
        {
          return getStatisticsValue("fibu.common.netto", getAuftragsStatistik().getNettoSum(),
              getAuftragsStatistik().getCounter());
        }
      }));
      fs.add(new DivTextPanel(fs.newChildId(), new Model<String>()
      {
        @Override
        public String getObject()
        {
          return WebConstants.HTML_TEXT_DIVIDER
              + getStatisticsValue("akquise", getAuftragsStatistik().getAkquiseSum(),
              getAuftragsStatistik().getCounterAkquise());
        }

      })
      {
        @Override
        public boolean isVisible()
        {
          return (getAuftragsStatistik().getCounterAkquise() > 0);
        }
      });
      fs.add(new DivTextPanel(fs.newChildId(), new Model<String>()
      {
        @Override
        public String getObject()
        {
          return WebConstants.HTML_TEXT_DIVIDER
              + getStatisticsValue("fibu.auftrag.status.beauftragt", getAuftragsStatistik().getBeauftragtSum(),
              getAuftragsStatistik()
                  .getCounterBeauftragt());
        }
      }, TextStyle.BLUE)
      {
        @Override
        public boolean isVisible()
        {
          return (getAuftragsStatistik().getCounterBeauftragt() > 0);
        }
      });
      fs.add(new DivTextPanel(fs.newChildId(), new Model<String>()
      {
        @Override
        public String getObject()
        {
          return WebConstants.HTML_TEXT_DIVIDER
              + getStatisticsValue("fibu.fakturiert", getAuftragsStatistik().getFakturiertSum(),
              getAuftragsStatistik().getCounterFakturiert());
        }
      })
      {
        @Override
        public boolean isVisible()
        {
          return (getAuftragsStatistik().getCounterFakturiert() > 0);
        }
      });
      fs.add(new DivTextPanel(fs.newChildId(), new Model<String>()
      {
        @Override
        public String getObject()
        {
          return WebConstants.HTML_TEXT_DIVIDER
              + getStatisticsValue("fibu.auftrag.filter.type.abgeschlossenNichtFakturiert",
              getAuftragsStatistik().getZuFakturierenSum(),
              getAuftragsStatistik().getCounterZuFakturieren());
        }
      }, TextStyle.RED)
      {
        @Override
        public boolean isVisible()
        {
          return (getAuftragsStatistik().getCounterZuFakturieren() > 0);
        }
      });
    }
  }

  /**
   * @see org.projectforge.web.wicket.AbstractListForm#onOptionsPanelCreate(org.projectforge.web.wicket.flowlayout.FieldsetPanel,
   * org.projectforge.web.wicket.flowlayout.DivPanel)
   */
  @SuppressWarnings("serial")
  @Override
  protected void onOptionsPanelCreate(final FieldsetPanel optionsFieldsetPanel, final DivPanel optionsCheckBoxesPanel)
  {
    // DropDownChoice years
    final YearListCoiceRenderer yearListChoiceRenderer = new YearListCoiceRenderer(auftragDao.getYears(), true);
    final DropDownChoice<Integer> yearChoice = new DropDownChoice<Integer>(optionsFieldsetPanel.getDropDownChoiceId(),
        new PropertyModel<Integer>(this,
            "year"),
        yearListChoiceRenderer.getYears(), yearListChoiceRenderer)
    {
      @Override
      protected boolean wantOnSelectionChangedNotifications()
      {
        return true;
      }

      @Override
      protected void onSelectionChanged(final Integer newSelection)
      {
        parentPage.refresh();
      }
    };
    yearChoice.setNullValid(false);
    optionsFieldsetPanel.add(yearChoice);

    // DropDownChoice listType
    final LabelValueChoiceRenderer<String> typeChoiceRenderer = new LabelValueChoiceRenderer<String>();
    for (final String str : AuftragFilter.LIST) {
      typeChoiceRenderer.addValue(str, getString("fibu.auftrag.filter.type." + str));
    }
    final DropDownChoice<String> typeChoice = new DropDownChoice<String>(optionsFieldsetPanel.getDropDownChoiceId(),
        new PropertyModel<String>(this,
            "searchFilter.listType"),
        typeChoiceRenderer.getValues(), typeChoiceRenderer)
    {
      @Override
      protected boolean wantOnSelectionChangedNotifications()
      {
        return true;
      }

      @Override
      protected void onSelectionChanged(final String newSelection)
      {
        parentPage.refresh();
      }
    };
    typeChoice.setNullValid(false);
    optionsFieldsetPanel.add(typeChoice);

    // DropDownChoice AuftragsPositionsArt
    createAuftragsPositionsArtMultiChoice(optionsFieldsetPanel);

    // DropDownChoice AuftragsPositionsPaymentType
    final LabelValueChoiceRenderer<Integer> auftragsPositionsPaymentTypeChoiceRenderer = new LabelValueChoiceRenderer<Integer>();
    auftragsPositionsPaymentTypeChoiceRenderer.addValue(-1, getString("filter.all"));
    for (final AuftragsPositionsPaymentType paymentType : AuftragsPositionsPaymentType.values()) {
      auftragsPositionsPaymentTypeChoiceRenderer.addValue(paymentType.ordinal(), getString(paymentType.getI18nKey()));
    }
    final DropDownChoice<Integer> auftragsPositionsPaymentTypeChoice = new DropDownChoice<Integer>(
        optionsFieldsetPanel.getDropDownChoiceId(),
        new PropertyModel<Integer>(this, "auftragsPositionsPaymentType"), auftragsPositionsPaymentTypeChoiceRenderer.getValues(),
        auftragsPositionsPaymentTypeChoiceRenderer)
    {
      @Override
      protected boolean wantOnSelectionChangedNotifications()
      {
        return true;
      }

      @Override
      protected void onSelectionChanged(final Integer newSelection)
      {
        parentPage.refresh();
      }
    };
    auftragsPositionsPaymentTypeChoice.setNullValid(false);
    optionsFieldsetPanel.add(auftragsPositionsPaymentTypeChoice);

    final UserSelectPanel userSelectPanel = new UserSelectPanel(optionsFieldsetPanel.newChildId(),
        new PropertyModel<PFUserDO>(this, "user"),
        parentPage, "user");
    optionsFieldsetPanel.add(userSelectPanel);
    userSelectPanel.init();
  }

  private void createAuftragsPositionsArtMultiChoice(final FieldsetPanel optionsFieldsetPanel)
  {
    final IModel<Collection<AuftragsPositionsArt>> auftragsPositionsArtenModel = new IModel<Collection<AuftragsPositionsArt>>()
    {
      @Override
      public Collection<AuftragsPositionsArt> getObject()
      {
        return getSearchFilter().getAuftragsPositionsArten();
      }

      @Override
      public void setObject(final Collection<AuftragsPositionsArt> auftragsPositionsArten)
      {
        getSearchFilter().setAuftragsPositionsArten(auftragsPositionsArten);
      }

      @Override
      public void detach()
      {
        // nothing to do
      }
    };

    final ChoiceProvider<AuftragsPositionsArt> choiceProvider = new TextChoiceProvider<AuftragsPositionsArt>()
    {
      @Override
      protected String getDisplayText(final AuftragsPositionsArt choice)
      {
        return getString(choice.getI18nKey());
      }

      @Override
      protected Object getId(final AuftragsPositionsArt choice)
      {
        return choice.name();
      }

      @Override
      public void query(final String term, final int page, final Response<AuftragsPositionsArt> response)
      {
        // TODO CT: need to handle page?
        final String termLowerCase = term.toLowerCase();
        final List<AuftragsPositionsArt> matchingAuftragsPositionsArten = Stream.of(AuftragsPositionsArt.values())
            .filter(art -> getString(art.getI18nKey()).toLowerCase().contains(termLowerCase))
            .collect(Collectors.toList());

        response.addAll(matchingAuftragsPositionsArten);
      }

      @Override
      public Collection<AuftragsPositionsArt> toChoices(final Collection<String> ids)
      {
        return ids.stream()
            .map(AuftragsPositionsArt::valueOf)
            .collect(Collectors.toList());
      }
    };

    final Select2MultiChoice<AuftragsPositionsArt> multiChoice = new Select2MultiChoice<>(Select2MultiChoicePanel.WICKET_ID, auftragsPositionsArtenModel,
        choiceProvider);

    optionsFieldsetPanel.add(new Select2MultiChoicePanel<>(optionsFieldsetPanel.newChildId(), multiChoice));
  }

  protected void refresh()
  {
    this.auftragsStatistik = null;
  }

  @Override
  public PFUserDO getUser()
  {
    return getSearchFilter().getUser();
  }

  public void setUser(final PFUserDO user)
  {
    getSearchFilter().setUser(user);
  }

  public Integer getYear()
  {
    return getSearchFilter().getYear();
  }

  public void setYear(final Integer year)
  {
    if (year == null) {
      getSearchFilter().setYear(-1);
    } else {
      getSearchFilter().setYear(year);
    }
  }

  public Integer getAuftragsPositionsPaymentType()
  {
    if (getSearchFilter().getAuftragsPositionsPaymentType() != null) {
      return getSearchFilter().getAuftragsPositionsPaymentType().ordinal();
    } else {
      return -1;
    }
  }

  public void setAuftragsPositionsPaymentType(final Integer auftragsPositionsPaymentType)
  {
    if (auftragsPositionsPaymentType == null || auftragsPositionsPaymentType == -1) {
      getSearchFilter().setAuftragsPositionsPaymentType(null);
    } else {
      getSearchFilter().setAuftragsPositionsPaymentType(AuftragsPositionsPaymentType.values()[auftragsPositionsPaymentType]);
    }
  }

  public AuftragListForm(final AuftragListPage parentPage)
  {
    super(parentPage);
  }

  private AuftragsStatistik getAuftragsStatistik()
  {
    if (auftragsStatistik == null) {
      auftragsStatistik = auftragDao.buildStatistik(getParentPage().getList());
    }
    return auftragsStatistik;
  }

  private String getStatisticsValue(final String label, final BigDecimal amount, final int count)
  {
    return getString(label) + ": " + CurrencyFormatter.format(amount) + " (" + count + ")";
  }

  @Override
  protected AuftragFilter newSearchFilterInstance()
  {
    return new AuftragFilter();
  }

  @Override
  protected Logger getLogger()
  {
    return log;
  }
}
