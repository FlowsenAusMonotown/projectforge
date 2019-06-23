import React from 'react';
import Select from 'react-select';
import { Card, CardBody, Col, Row } from 'reactstrap';
import EditableMultiValueLabel from '../../../components/base/page/layout/EditableMultiValueLabel';
import LoadingContainer from '../../../components/design/loading-container';
import { getServiceURL, handleHTTPErrors } from '../../../utilities/rest';
import CalendarFilterSettings from '../../panel/calendar/CalendarFilterSettings';
import CalendarPanel from '../../panel/calendar/CalendarPanel';
import FavoritesPanel from '../../panel/FavoritesPanel';
import { customStyles } from './Calendar.module';
import { CalendarContext, defaultValues as calendarContextDefaultValues } from './CalendarContext';

class CalendarPage extends React.Component {
    constructor(props) {
        super(props);

        this.state = {
            colors: {},
            date: new Date(),
            view: 'week',
            teamCalendars: undefined,
            activeCalendars: [],
            listOfDefaultCalendars: [],
            defaultCalendar: undefined,
            filterFavorites: undefined,
            isCurrentFilterModified: false,
            translations: undefined,
        };

        this.fetchInitial = this.fetchInitial.bind(this);
        this.onChange = this.onChange.bind(this);
        this.handleMultiValueChange = this.handleMultiValueChange.bind(this);
        this.changeDefaultCalendar = this.changeDefaultCalendar.bind(this);
        this.onFavoriteCreate = this.onFavoriteCreate.bind(this);
        this.onFavoriteDelete = this.onFavoriteDelete.bind(this);
        this.onFavoriteRename = this.onFavoriteRename.bind(this);
        this.onFavoriteSelect = this.onFavoriteSelect.bind(this);
        this.onFavoriteUpdate = this.onFavoriteUpdate.bind(this);
        this.saveUpdateResponseInState = this.saveUpdateResponseInState.bind(this);
    }

    componentDidMount() {
        this.fetchInitial();
    }

    onChange(activeCalendars) {
        activeCalendars.sort((a, b) => a.title.localeCompare(b.title));
        this.setState({
            activeCalendars,
            isCurrentFilterModified: true,
        });
    }

    onFavoriteCreate(newFilterName) {
        fetch(getServiceURL('calendar/createNewFilter',
            { newFilterName }), {
            method: 'GET',
            credentials: 'include',
            headers: {
                Accept: 'application/json',
            },
        })
            .then(handleHTTPErrors)
            .then(response => response.json())
            .then(this.saveUpdateResponseInState)
            .catch(error => alert(`Internal error: ${error}`));
    }

    onFavoriteDelete(id) {
        fetch(getServiceURL('calendar/deleteFilter',
            { id }), {
            method: 'GET',
            credentials: 'include',
            headers: {
                Accept: 'application/json',
            },
        })
            .then(handleHTTPErrors)
            .then(response => response.json())
            .then(this.saveUpdateResponseInState)
            .catch(error => alert(`Internal error: ${error}`));
    }

    onFavoriteSelect(id) {
        fetch(getServiceURL('calendar/selectFilter',
            { id }), {
            method: 'GET',
            credentials: 'include',
            headers: {
                Accept: 'application/json',
            },
        })
            .then(handleHTTPErrors)
            .then(response => response.json())
            .then(this.saveUpdateResponseInState)
            .catch(error => alert(`Internal error: ${error}`));
    }

    onFavoriteRename(id, newName) {
        console.log(id, newName);
    }

    onFavoriteUpdate(id) {
        fetch(getServiceURL('calendar/updateFilter',
            { id }), {
            method: 'GET',
            credentials: 'include',
            headers: {
                Accept: 'application/json',
            },
        })
            .then(handleHTTPErrors)
            .then(response => response.json())
            .then(this.saveUpdateResponseInState)
            .catch(error => alert(`Internal error: ${error}`));
    }

    changeDefaultCalendar(defaultCalendar) {
        this.setState({ defaultCalendar });
    }

    fetchInitial() {
        this.setState({ loading: true });
        fetch(getServiceURL('calendar/initial'), {
            method: 'GET',
            credentials: 'include',
            headers: {
                Accept: 'application/json',
            },
        })
            .then(handleHTTPErrors)
            .then(response => response.json())
            .then(this.saveUpdateResponseInState)
            // TODO: ERROR HANDLING
            .catch(error => alert(`Internal error: ${error}`));
    }

    saveUpdateResponseInState(json) {
        const newState = {
            loading: false,
            ...json,
        };

        if (newState.date) {
            newState.date = new Date(newState.date);
        }

        this.setState(newState);
    }

    handleMultiValueChange(id, newValue) {
        this.setState(({ colors }) => ({
            colors: {
                ...colors,
                [id]: newValue,
            },
        }));
    }

    render() {
        const {
            activeCalendars,
            listOfDefaultCalendars,
            isCurrentFilterModified,
            colors,
            currentFilter,
            date,
            filterFavorites,
            loading,
            teamCalendars,
            translations,
            view,
        } = this.state;

        if (!translations) {
            return <div>...</div>;
        }

        const options = teamCalendars.map(option => ({
            ...option,
            filterType: 'COLOR_PICKER',
            label: option.title,
        }));

        return (
            <LoadingContainer loading={loading}>
                <CalendarContext.Provider
                    value={{
                        ...calendarContextDefaultValues,
                        saveUpdateResponseInState: this.saveUpdateResponseInState,
                    }}
                >
                    <Card>
                        <CardBody>
                            <form>
                                <Row>
                                    <Col sm="11">
                                        <Select
                                            closeMenuOnSelect={false}
                                            components={{
                                                MultiValueLabel: EditableMultiValueLabel,
                                            }}
                                            getOptionLabel={option => (option.title)}
                                            getOptionValue={option => (option.id)}
                                            isClearable
                                            isMulti
                                            onChange={this.onChange}
                                            options={options}
                                            placeholder={translations['select.placeholder']}
                                            setMultiValue={this.handleMultiValueChange}
                                            styles={customStyles}
                                            values={colors}
                                            value={activeCalendars.map(option => ({
                                                ...option,
                                                filterType: 'COLOR_PICKER',
                                                label: option.title,
                                            }))}
                                            // loadOptions={loadOptions}
                                            // defaultOptions={defaultOptions}
                                        />
                                    </Col>
                                    <Col sm="1" className="d-flex align-items-center">
                                        <FavoritesPanel
                                            onFavoriteCreate={this.onFavoriteCreate}
                                            onFavoriteDelete={this.onFavoriteDelete}
                                            onFavoriteRename={this.onFavoriteRename}
                                            onFavoriteSelect={this.onFavoriteSelect}
                                            onFavoriteUpdate={this.onFavoriteUpdate}
                                            favorites={filterFavorites}
                                            translations={translations}
                                            currentFavoriteId={currentFilter.id}
                                            isModified={isCurrentFilterModified}
                                            closeOnSelect={false}
                                        />
                                        <CalendarFilterSettings
                                            listOfDefaultCalendars={listOfDefaultCalendars}
                                            translations={translations}
                                        />
                                    </Col>
                                </Row>
                            </form>
                        </CardBody>
                    </Card>
                    <CalendarPanel
                        defaultDate={date}
                        defaultView={view}
                        activeCalendars={activeCalendars}
                        topHeight="225px"
                        translations={translations}
                    />
                </CalendarContext.Provider>
            </LoadingContainer>
        );
    }
}

CalendarPage.propTypes = {};

CalendarPage.defaultProps = {};

export default CalendarPage;
